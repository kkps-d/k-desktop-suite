// macOS: swift -module-cache-path /tmp/kd-showcase-swift-cache web/capture/prepare-images.swift
// Crops only recorded window bounds, exports JPEGs, and builds review sheets.
import AppKit
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let records = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("capture/inventory.json"))) as! [[String: Any]]
func load(_ url: URL) -> CGImage {
    CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithURL(url as CFURL, nil)!, 0, nil)!
}
func save(_ image: CGImage, _ url: URL, type: CFString, quality: Double = 0.9) {
    let output = CGImageDestinationCreateWithURL(url as CFURL, type, 1, nil)!
    CGImageDestinationAddImage(output, image, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
    precondition(CGImageDestinationFinalize(output))
}
for r in records {
    let image = load(root.appendingPathComponent(r["original"] as! String))
    let b = r["crop"] as! [Int]
    let cropped = image.cropping(to: CGRect(x: b[0], y: b[1], width: b[2], height: b[3]))!
    save(cropped, root.appendingPathComponent(r["optimized"] as! String), type: UTType.jpeg.identifier as CFString)
}
for app in ["notepad", "paint"] {
    let group = records.filter { $0["app"] as? String == app }
    let columns = 3, tileW = 450, tileH = 300
    let width = columns * tileW, height = ((group.count + columns - 1) / columns) * tileH
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.setFillColor(CGColor(gray: 0.94, alpha: 1)); context.fill(CGRect(x: 0, y: 0, width: width, height: height))
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: false)
    for (index, r) in group.enumerated() {
        let image = load(root.appendingPathComponent(r["optimized"] as! String))
        let scale = min(430.0 / Double(image.width), 258.0 / Double(image.height))
        let w = Double(image.width) * scale, h = Double(image.height) * scale
        let x = Double((index % columns) * tileW), y = Double(height - ((index / columns) + 1) * tileH)
        context.draw(image, in: CGRect(x: x + (450 - w) / 2, y: y + 34 + (258 - h) / 2, width: w, height: h))
        let label = r["id"] as! String
        (label as NSString).draw(at: NSPoint(x: x + 12, y: y + 12), withAttributes: [.font: NSFont.systemFont(ofSize: 12), .foregroundColor: NSColor.black])
    }
    NSGraphicsContext.restoreGraphicsState()
    save(context.makeImage()!, root.appendingPathComponent("capture/\(app)-contact-sheet.jpg"), type: UTType.jpeg.identifier as CFString)
}
print("Prepared \(records.count) window crops and two contact sheets.")
