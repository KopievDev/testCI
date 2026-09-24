import Foundation
import SwiftUI

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
}
