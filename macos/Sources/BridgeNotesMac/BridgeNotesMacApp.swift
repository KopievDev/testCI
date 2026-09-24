import SwiftUI

@main
@MainActor
struct BridgeNotesMacApp: App {
    @StateObject private var store: NotesStore
    @StateObject private var server: BridgeServer

    init() {
        let store = NotesStore()
        _store = StateObject(wrappedValue: store)
        _server = StateObject(wrappedValue: BridgeServer(store: store))
    }

    var body: some Scene {
        WindowGroup {
            ContentView(store: store, server: server)
                .frame(minWidth: 860, minHeight: 560)
                .onAppear { server.start() }
        }
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) { }
        }
    }
}
