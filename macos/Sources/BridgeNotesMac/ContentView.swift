import SwiftUI

struct ContentView: View {
    @ObservedObject var store: NotesStore
    @ObservedObject var server: BridgeServer

    @State private var selectedID: String?
    @State private var search = ""
    @State private var draft = Note.empty()
    @State private var hasDraft = false

    private var filtered: [Note] {
        let q = search.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return store.visibleNotes }
        return store.visibleNotes.filter {
            ($0.title + " " + $0.body).lowercased().contains(q)
        }
    }

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                HStack {
                    Text("BridgeNotes")
                        .font(.title2.bold())
                    Spacer()
                    Button {
                        newNote()
                    } label: {
                        Image(systemName: "square.and.pencil")
                    }
                    .buttonStyle(.borderless)
                    .help("Новая заметка")
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 10)

                TextField("Поиск", text: $search)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)

                List(filtered, selection: $selectedID) { note in
                    VStack(alignment: .leading, spacing: 4) {
                        Text((note.pinned ? "★ " : "") + displayTitle(note))
                            .font(.headline)
                            .lineLimit(1)
                        if !note.body.isEmpty {
                            Text(note.body.replacingOccurrences(of: "\n", with: " "))
                                .foregroundStyle(.secondary)
                                .font(.caption)
                                .lineLimit(2)
                        }
                        Text(Date(timeIntervalSince1970: TimeInterval(note.updatedAt) / 1000), style: .relative)
                            .foregroundStyle(.tertiary)
                            .font(.caption2)
                    }
                    .padding(.vertical, 3)
                    .tag(note.id)
                }
                .listStyle(.sidebar)

                serverPanel
            }
            .frame(minWidth: 270)
        } detail: {
            editor
                .frame(minWidth: 520, minHeight: 420)
        }
        .onChange(of: selectedID) { newValue in
            guard let note = store.note(id: newValue) else { return }
            draft = note
            hasDraft = true
        }
        .onAppear {
            if selectedID == nil, let first = store.visibleNotes.first {
                selectedID = first.id
                draft = first
                hasDraft = true
            }
        }
    }

    private var serverPanel: some View {
        VStack(alignment: .leading, spacing: 5) {
            Divider()
            HStack(spacing: 6) {
                Circle()
                    .fill(server.isRunning ? Color.green : Color.red)
                    .frame(width: 8, height: 8)
                Text(server.isRunning ? "Сервер запущен" : "Сервер остановлен")
                    .font(.caption.bold())
            }
            if let address = server.addresses.first {
                Text(address)
                    .font(.caption2.monospaced())
                    .textSelection(.enabled)
            }
            HStack {
                Text("Код: \(server.pairingCode)")
                    .font(.caption.monospaced().bold())
                    .textSelection(.enabled)
                Spacer()
                Button("Новый код") { server.regeneratePairingCode() }
                    .font(.caption2)
                    .buttonStyle(.link)
            }
            if let last = server.lastSync {
                Text("Последняя синхронизация: \(last.formatted(date: .omitted, time: .shortened))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            } else {
                Text("Android подключится автоматически после настройки")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
    }

    private var editor: some View {
        Group {
            if hasDraft {
                VStack(alignment: .leading, spacing: 12) {
                    TextField("Заголовок", text: $draft.title)
                        .font(.title2.bold())
                        .textFieldStyle(.plain)

                    Divider()

                    TextEditor(text: $draft.body)
                        .font(.body)
                        .scrollContentBackground(.hidden)
                        .background(Color.clear)

                    Divider()

                    HStack {
                        Toggle("Закрепить", isOn: $draft.pinned)
                            .toggleStyle(.checkbox)
                        Spacer()
                        Button(role: .destructive) {
                            deleteCurrent()
                        } label: {
                            Label("Удалить", systemImage: "trash")
                        }
                        Button {
                            saveCurrent()
                        } label: {
                            Label("Сохранить", systemImage: "checkmark.circle.fill")
                        }
                        .keyboardShortcut("s", modifiers: .command)
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(24)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "note.text")
                        .font(.system(size: 46))
                        .foregroundStyle(.secondary)
                    Text("Выберите заметку или создайте новую")
                        .foregroundStyle(.secondary)
                    Button("Новая заметка") { newNote() }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
    }

    private func newNote() {
        let note = store.createNote()
        selectedID = note.id
        draft = note
        hasDraft = true
    }

    private func saveCurrent() {
        guard hasDraft else { return }
        store.save(draft)
        if let latest = store.note(id: draft.id) { draft = latest }
    }

    private func deleteCurrent() {
        guard let id = selectedID else { return }
        store.delete(id: id)
        selectedID = store.visibleNotes.first?.id
        if let id = selectedID, let note = store.note(id: id) {
            draft = note
            hasDraft = true
        } else {
            hasDraft = false
        }
    }

    private func displayTitle(_ note: Note) -> String {
        if !note.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return note.title }
        let first = note.body.split(separator: "\n", maxSplits: 1).first.map(String.init) ?? ""
        return first.isEmpty ? "Без названия" : first
    }
}
