import Foundation
import Network
import Security
import Darwin

final class BridgeServer: ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var pairingCode = "------"
    @Published private(set) var addresses: [String] = []
    @Published private(set) var lastSync: Date?

    private let store: NotesStore
    private let queue = DispatchQueue(label: "dev.kopiev.bridgenotes.server")
    private var listener: NWListener?
    private let port: UInt16 = 8787
    private let tokenURL: URL

    init(store: NotesStore) {
        self.store = store
        let fm = FileManager.default
        let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("BridgeNotes", isDirectory: true)
        try? fm.createDirectory(at: base, withIntermediateDirectories: true)
        tokenURL = base.appendingPathComponent("pairing-code.txt")
        pairingCode = Self.loadOrCreateToken(at: tokenURL)
        addresses = Self.localIPv4Addresses().map { "http://\($0):\(port)" }
    }

    func start() {
        guard listener == nil else { return }
        do {
            let nwPort = NWEndpoint.Port(rawValue: port)!
            let listener = try NWListener(using: .tcp, on: nwPort)
            self.listener = listener
            listener.newConnectionHandler = { [weak self] connection in
                self?.handle(connection)
            }
            listener.stateUpdateHandler = { [weak self] state in
                DispatchQueue.main.async {
                    guard let self else { return }
                    switch state {
                    case .ready:
                        self.isRunning = true
                        self.addresses = Self.localIPv4Addresses().map { "http://\($0):\(self.port)" }
                    case .failed, .cancelled:
                        self.isRunning = false
                    default:
                        break
                    }
                }
            }
            listener.start(queue: queue)
        } catch {
            DispatchQueue.main.async { self.isRunning = false }
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
        isRunning = false
    }

    func regeneratePairingCode() {
        pairingCode = Self.newToken()
        try? pairingCode.write(to: tokenURL, atomically: true, encoding: .utf8)
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1_048_576) { [weak self] data, _, complete, error in
            guard let self else { return }
            var combined = buffer
            if let data { combined.append(data) }

            if self.isCompleteHTTPRequest(combined) || complete || error != nil {
                self.process(combined, on: connection)
            } else {
                self.receive(on: connection, buffer: combined)
            }
        }
    }

    private func isCompleteHTTPRequest(_ data: Data) -> Bool {
        guard let string = String(data: data, encoding: .utf8),
              let headerRange = string.range(of: "\r\n\r\n") else { return false }
        let header = String(string[..<headerRange.lowerBound])
        let contentLength = header
            .split(separator: "\n")
            .first { $0.lowercased().hasPrefix("content-length:") }
            .flatMap { Int($0.split(separator: ":", maxSplits: 1).last?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "0") } ?? 0
        let headerBytes = string[..<headerRange.upperBound].utf8.count
        return data.count >= headerBytes + contentLength
    }

    private func process(_ data: Data, on connection: NWConnection) {
        guard let raw = String(data: data, encoding: .utf8),
              let split = raw.range(of: "\r\n\r\n") else {
            send(status: "400 Bad Request", json: ["error": "bad_request"], on: connection)
            return
        }

        let headerText = String(raw[..<split.lowerBound])
        let bodyText = String(raw[split.upperBound...])
        let lines = headerText.components(separatedBy: "\r\n")
        guard let request = lines.first?.split(separator: " "), request.count >= 2 else {
            send(status: "400 Bad Request", json: ["error": "bad_request"], on: connection)
            return
        }

        let method = String(request[0])
        let path = String(request[1])
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            let parts = line.split(separator: ":", maxSplits: 1)
            if parts.count == 2 {
                headers[String(parts[0]).lowercased()] = String(parts[1]).trimmingCharacters(in: .whitespaces)
            }
        }

        if method == "GET" && path == "/api/health" {
            send(status: "200 OK", json: ["status": "ok", "service": "BridgeNotesMac"], on: connection)
            return
        }

        guard method == "POST", path == "/api/sync" else {
            send(status: "404 Not Found", json: ["error": "not_found"], on: connection)
            return
        }

        guard headers["x-bridge-token"] == pairingCode else {
            send(status: "401 Unauthorized", json: ["error": "invalid_pairing_code"], on: connection)
            return
        }

        guard let body = bodyText.data(using: .utf8),
              let payload = try? JSONDecoder().decode(SyncPayload.self, from: body) else {
            send(status: "400 Bad Request", json: ["error": "invalid_json"], on: connection)
            return
        }

        Task { @MainActor in
            let merged = self.store.merge(payload.notes)
            self.lastSync = Date()
            self.sendPayload(SyncPayload(notes: merged), on: connection)
        }
    }

    private func sendPayload(_ payload: SyncPayload, on connection: NWConnection) {
        guard let data = try? JSONEncoder().encode(payload) else {
            send(status: "500 Internal Server Error", json: ["error": "encode_failed"], on: connection)
            return
        }
        send(status: "200 OK", body: data, contentType: "application/json; charset=utf-8", on: connection)
    }

    private func send(status: String, json: [String: String], on connection: NWConnection) {
        let data = (try? JSONSerialization.data(withJSONObject: json)) ?? Data("{}".utf8)
        send(status: status, body: data, contentType: "application/json; charset=utf-8", on: connection)
    }

    private func send(status: String, body: Data, contentType: String, on connection: NWConnection) {
        let header = "HTTP/1.1 \(status)\r\nContent-Type: \(contentType)\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        var response = Data(header.utf8)
        response.append(body)
        connection.send(content: response, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static func loadOrCreateToken(at url: URL) -> String {
        if let token = try? String(contentsOf: url, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines),
           token.count == 6 {
            return token
        }
        let token = newToken()
        try? token.write(to: url, atomically: true, encoding: .utf8)
        return token
    }

    private static func newToken() -> String {
        var value: UInt32 = 0
        _ = SecRandomCopyBytes(kSecRandomDefault, MemoryLayout<UInt32>.size, &value)
        return String(format: "%06u", value % 1_000_000)
    }

    private static func localIPv4Addresses() -> [String] {
        var result: [String] = []
        var pointer: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&pointer) == 0, let first = pointer else { return result }
        defer { freeifaddrs(pointer) }

        for item in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let interface = item.pointee
            guard let addr = interface.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: interface.ifa_name)
            guard name != "lo0" else { continue }

            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let length = socklen_t(addr.pointee.sa_len)
            if getnameinfo(addr, length, &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                let ip = String(cString: host)
                if !ip.hasPrefix("127.") && !result.contains(ip) { result.append(ip) }
            }
        }
        return result.sorted()
    }
}
