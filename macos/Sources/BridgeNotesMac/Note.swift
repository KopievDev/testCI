import Foundation

struct Note: Identifiable, Codable, Hashable {
    var id: String
    var title: String
    var body: String
    var pinned: Bool
    var deleted: Bool
    var createdAt: Int64
    var updatedAt: Int64

    static func empty() -> Note {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return Note(
            id: UUID().uuidString,
            title: "",
            body: "",
            pinned: false,
            deleted: false,
            createdAt: now,
            updatedAt: now
        )
    }
}

struct SyncPayload: Codable {
    var notes: [Note]
}
