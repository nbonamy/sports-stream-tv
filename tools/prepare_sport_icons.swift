import Foundation
import Vision
import CoreImage
import ImageIO
import UniformTypeIdentifiers

// macOS 14+: preserve original artwork, extract its foreground, then give every
// sport the same transparent canvas and centered aspect-preserving content box.
guard CommandLine.arguments.count == 3 else {
    fatalError("Usage: swift tools/prepare_sport_icons.swift <source-directory> <output-directory>")
}
let source = URL(fileURLWithPath: CommandLine.arguments[1])
let output = URL(fileURLWithPath: CommandLine.arguments[2])
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
let context = CIContext()
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
let sports = ["football", "tennis", "rugby", "f1", "golf", "nfl", "nba", "mlb", "nhl", "more"]

for sport in sports {
    let handler = VNImageRequestHandler(url: source.appendingPathComponent("sport_\(sport).png"))
    let request = VNGenerateForegroundInstanceMaskRequest()
    try handler.perform([request])
    guard let result = request.results?.first, !result.allInstances.isEmpty else {
        fatalError("No foreground found for \(sport)")
    }
    let buffer = try result.generateMaskedImage(ofInstances: result.allInstances, from: handler, croppedToInstancesExtent: true)
    let image = CIImage(cvPixelBuffer: buffer)
    guard let foreground = context.createCGImage(image, from: image.extent) else {
        fatalError("Could not render \(sport)")
    }
    let canvas = CGContext(data: nil, width: 512, height: 384, bitsPerComponent: 8, bytesPerRow: 0,
                          space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    canvas.interpolationQuality = .high
    let scale = min(480.0 / Double(foreground.width), 352.0 / Double(foreground.height))
    let width = Double(foreground.width) * scale
    let height = Double(foreground.height) * scale
    canvas.draw(foreground, in: CGRect(x: (512 - width) / 2, y: (384 - height) / 2, width: width, height: height))
    let destination = output.appendingPathComponent("sport_\(sport)_cutout.png")
    let writer = CGImageDestinationCreateWithURL(destination as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(writer, canvas.makeImage()!, nil)
    guard CGImageDestinationFinalize(writer) else { fatalError("Could not save \(sport)") }
    print("\(sport): \(foreground.width)×\(foreground.height) foreground → 512×384 RGBA")
}
