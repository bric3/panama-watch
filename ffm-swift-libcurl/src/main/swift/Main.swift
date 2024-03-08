import Foundation
import Cocoa
import SwiftUI
import SwiftTypes

@_cdecl("test")
public func test(p:Point2D) -> Point2D{

    let app = NSApplication.shared

    let view = VStack {
        Text("The input data is a Point(\(p.x),\(p.y))");
        Button("Exit") {app.stop(app)};
    }.frame(maxWidth: .infinity, maxHeight: .infinity)

    app.run {view}

    let r = Point2D(x:p.y,y:p.x) //swap x & y

    return r
}

extension NSApplication {
    public func run<V: View>(@ViewBuilder view: () -> V) {
        let appDelegate = AppDelegate(view())
        NSApp.setActivationPolicy(.regular)
        delegate = appDelegate
        run()
    }
}

class AppDelegate<V: View>: NSObject, NSApplicationDelegate, NSWindowDelegate {
    init(_ contentView: V) {
        self.contentView = contentView

    }
    var window: NSWindow!
    var hostingView: NSView?
    var contentView: V

    func applicationDidFinishLaunching(_ notification: Notification) {
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 480, height: 300),
            styleMask: [.titled, .closable, .miniaturizable, .resizable, .fullSizeContentView],
            backing: .buffered, defer: false)
        window.center()
        window.setFrameAutosaveName("Arena")
        hostingView = NSHostingView(rootView: contentView)
        window.contentView = hostingView
        window.makeKeyAndOrderFront(nil)
        window.delegate = self
        NSApp.activate(ignoringOtherApps: true)
    }
}