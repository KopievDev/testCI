import Foundation
import SwiftUI
import SQLite3

@MainActor
final class NotesStore: ObservableObject {
    @Published private(set) var notes: [Note] = []

    private let fileURL: URL

    init() {
        let fm = FileManager.default
        let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("BridgeNotes", isDirectory: true)
        try? fm.createDirectory(at: base, withIntermediateDirectories: true)
        fileURL = base.appendingPathComponent("notes.json")
        migrateLegacyDatabaseIfNeeded()
        load()
    }

    var visibleNotes: [Note] {
        notes.filter { !$0.deleted }.sorted {
            if $0.pinned != $1.pinned { return $0.pinned && !$1.pinned }
            return $0.updatedAt > $1.updatedAt
        }
    }

    func note(id: String?) -> Note? {
        guard let id else { return nil }
        return notes.first { $0.id == id && !$0.deleted }
    }

    @discardableResult
    func createNote() -> Note {
        let note = Note.empty()
        notes.append(note)
        persist()
        return note
    }

    func save(_ note: Note) {
        var copy = note
        copy.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        if let idx = notes.firstIndex(where: { $0.id == copy.id }) {
            notes[idx] = copy
        } else {
            notes.append(copy)
        }
        persist()
    }

    func delete(id: String) {
        guard let idx = notes.firstIndex(where: { $0.id == id }) else { return }
        notes[idx].deleted = true
        notes[idx].updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        persist()
    }

    func merge(_ remote: [Note]) -> [Note] {
        var changed = false
        for incoming in remote {
            if let idx = notes.firstIndex(where: { $0.id == incoming.id }) {
                if incoming.updatedAt > notes[idx].updatedAt {
                    notes[idx] = incoming
                    changed = true
                }
            } else {
                notes.append(incoming)
                changed = true
            }
        }
        if changed { persist() }
        return notes.sorted { $0.updatedAt > $1.updatedAt }
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([Note].self, from: data) else {
            notes = []
            return
        }
        notes = decoded
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(notes) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private func migrateLegacyDatabaseIfNeeded() {
        let fm = FileManager.default
        guard !fm.fileExists(atPath: fileURL.path) else { return }

        let home = fm.homeDirectoryForCurrentUser
        let candidates = [
            home.appendingPathComponent("Downloads/BridgeNotes-Mac/bridge_notes.db"),
            home.appendingPathComponent("Downloads/BridgeNotes-Mac-Companion/bridge_notes.db")
        ]

        for url in candidates where fm.fileExists(atPath: url.path) {
            var db: OpaquePointer?
            guard sqlite3_open_v2(url.path, &db, SQLITE_OPEN_READONLY, nil) == SQLITE_OK,
                  let db else { continue }
            defer { sqlite3_close(db) }

            let sql = "SELECT id,title,body,pinned,deleted,created_at,updated_at FROM notes"
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK,
                  let statement else { continue }
            defer { sqlite3_finalize(statement) }

            var imported: [Note] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                func text(_ index: Int32) -> String {
                    guard let ptr = sqlite3_column_text(statement, index) else { return "" }
                    return String(cString: ptr)
                }

                imported.append(Note(
                    id: text(0),
                    title: text(1),
                    body: text(2),
                    pinned: sqlite3_column_int(statement, 3) != 0,
                    deleted: sqlite3_column_int(statement, 4) != 0,
                    createdAt: sqlite3_column_int64(statement, 5),
                    updatedAt: sqlite3_column_int64(statement, 6)
                ))
            }

            if !imported.isEmpty,
               let data = try? JSONEncoder().encode(imported) {
                try? data.write(to: fileURL, options: .atomic)
                return
            }
        }
    }
}
