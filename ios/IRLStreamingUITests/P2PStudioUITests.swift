import CoreGraphics
import UIKit
import XCTest

/// PT: Testes de interface do estúdio P2P. Usam identificadores estáveis para
/// PT: não dependerem do idioma escolhido na app.
/// EN: UI tests for the P2P studio. They use stable identifiers so they do not
/// EN: depend on the language selected in the app.
final class P2PStudioUITests: XCTestCase {
    private let fictionalID = "irl-test-4f2a"

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// PT: Usa preferências isoladas e limpas em cada arranque.
    /// EN: Uses isolated, clean preferences on every launch.
    private func makeApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTest", "-resetSettings"]
        return app
    }

    /// PT: Abre o separador P2P, preenche um ID fictício e entra no estúdio.
    /// EN: Opens the P2P tab, fills in a fictional ID and enters the studio.
    private func openStudio(_ app: XCUIApplication) {
        let tab = app.tabBars.buttons["P2P"].exists
            ? app.tabBars.buttons["P2P"]
            : app.buttons["P2P"]
        XCTAssertTrue(tab.waitForExistence(timeout: 15), "P2P tab not found")
        tab.tap()

        let field = app.textFields["p2p.streamId"]
        XCTAssertTrue(field.waitForExistence(timeout: 15), "Stream ID field not found")
        field.tap()
        field.typeText(fictionalID + "\n")

        let open = app.buttons["p2p.open"]
        XCTAssertTrue(open.waitForExistence(timeout: 10), "Open studio button not found")
        if !open.isHittable {
            app.swipeUp()
        }
        open.tap()

        XCTAssertTrue(app.buttons["p2p.mute"].waitForExistence(timeout: 30),
                      "P2P controls did not appear")
    }

    @discardableResult
    private func waitForValue(_ element: XCUIElement, _ value: String, timeout: TimeInterval = 5) -> Bool {
        let predicate = NSPredicate(format: "value == %@", value)
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }

    /// PT: Luminância média do centro da captura, para detetar um preview preto.
    /// EN: Average luminance of the screenshot centre, to detect a black preview.
    private func centreBrightness(_ screenshot: XCUIScreenshot) -> Double {
        guard let image = screenshot.image.cgImage else { return -1 }
        let width = CGFloat(image.width)
        let height = CGFloat(image.height)
        let cropRect = CGRect(x: width * 0.25, y: height * 0.30,
                              width: width * 0.50, height: height * 0.30)
        guard let crop = image.cropping(to: cropRect) else { return -1 }

        var pixel = [UInt8](repeating: 0, count: 4)
        guard let context = CGContext(data: &pixel, width: 1, height: 1,
                                      bitsPerComponent: 8, bytesPerRow: 4,
                                      space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return -1
        }
        context.interpolationQuality = .medium
        context.draw(crop, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        return (Double(pixel[0]) + Double(pixel[1]) + Double(pixel[2])) / 3
    }

    private func attach(_ screenshot: XCUIScreenshot, name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// PT: Espera que o preview deixe de estar preto (pode demorar depois de
    /// PT: trocar de câmara). Falha o teste se continuar preto.
    /// EN: Waits for the preview to stop being black (it can take a while after
    /// EN: switching cameras). Fails the test if it stays black.
    private func waitForLivePreview(timeout: TimeInterval = 20) -> XCUIScreenshot {
        let deadline = Date().addingTimeInterval(timeout)
        var screenshot = XCUIScreen.main.screenshot()
        while Date() < deadline {
            if centreBrightness(screenshot) > 12 { return screenshot }
            Thread.sleep(forTimeInterval: 0.5)
            screenshot = XCUIScreen.main.screenshot()
        }
        XCTFail("Preview stayed black for \(timeout)s (brightness \(centreBrightness(screenshot)))")
        return screenshot
    }

    /// PT: Valida os cinco controlos, o mudo, a folha de qualidade e o fecho.
    /// EN: Validates the five controls, mute, the quality sheet and closing.
    func testP2PStudioControlsAndQuality() throws {
        let app = makeApp()
        app.launch()
        openStudio(app)

        for identifier in ["p2p.mute", "p2p.flip", "p2p.torch", "p2p.record", "p2p.quality"] {
            let button = app.buttons[identifier]
            XCTAssertTrue(button.exists, "Missing control \(identifier)")
            XCTAssertTrue(button.isHittable, "Control \(identifier) is not hittable")
        }

        // PT: O estado do mudo é publicado como valor de acessibilidade.
        // EN: The mute state is exposed as an accessibility value.
        let mute = app.buttons["p2p.mute"]
        XCTAssertTrue(waitForValue(mute, "off"))
        mute.tap()
        XCTAssertTrue(waitForValue(mute, "on"))
        mute.tap()
        XCTAssertTrue(waitForValue(mute, "off"))

        // PT: A folha de qualidade abre e fecha sem recarregar o estúdio.
        // EN: The quality sheet opens and closes without reloading the studio.
        app.buttons["p2p.quality"].tap()
        let cancel = app.buttons["p2p.quality.cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 10), "Quality sheet did not open")
        XCTAssertTrue(app.buttons["p2p.quality.apply"].exists, "Apply button is missing")
        cancel.tap()
        XCTAssertTrue(app.buttons["p2p.mute"].waitForExistence(timeout: 10),
                      "Studio did not return after closing the quality sheet")

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "p2p-controls"
        attachment.lifetime = .keepAlways
        add(attachment)

        // PT: Recarregar mantém o WebView e os controlos disponíveis.
        // EN: Reloading keeps the WebView and the controls available.
        app.buttons["p2p.reload"].tap()
        XCTAssertTrue(app.buttons["p2p.mute"].waitForExistence(timeout: 30),
                      "Controls disappeared after reload")

        // PT: Fechar volta ao formulário de configuração.
        // EN: Closing returns to the setup form.
        app.buttons["p2p.close"].tap()
        XCTAssertTrue(app.textFields["p2p.streamId"].waitForExistence(timeout: 15),
                      "Studio did not close")
    }

    /// PT: Percorre as câmaras até a traseira estar ativa.
    /// EN: Cycles through the cameras until the back one is active.
    private func selectBackCamera(_ app: XCUIApplication) {
        let flip = app.buttons["p2p.flip"]
        for _ in 0..<5 {
            if (flip.value as? String) == "back" { return }
            flip.tap()
            Thread.sleep(forTimeInterval: 1.2)
        }
        XCTAssertEqual(flip.value as? String, "back", "Could not select the back camera")
    }

    /// PT: O botão da câmara tem de indicar B (traseira) e F (frontal).
    /// EN: The camera button must show B (back) and F (front).
    func testCameraButtonShowsFacingLetter() throws {
        let app = makeApp()
        app.launch()
        openStudio(app)

        let flip = app.buttons["p2p.flip"]
        var seen = Set<String>()
        for _ in 0..<5 {
            if let value = flip.value as? String, value != "unknown" {
                seen.insert(value)
            }
            flip.tap()
            Thread.sleep(forTimeInterval: 1.2)
        }
        if let value = flip.value as? String, value != "unknown" {
            seen.insert(value)
        }

        XCTAssertTrue(seen.contains("back"), "The B/back state was never reported: \(seen)")
        XCTAssertTrue(seen.contains("front"), "The F/front state was never reported: \(seen)")
    }

    /// PT: Ligar a lanterna não pode deixar o preview preto.
    /// EN: Turning the torch on must not black out the preview.
    func testTorchKeepsPreviewVisible() throws {
        let app = makeApp()
        app.launch()
        openStudio(app)
        selectBackCamera(app)

        let torch = app.buttons["p2p.torch"]
        XCTAssertTrue(waitForValue(torch, "off"), "Torch should start off")

        let before = waitForLivePreview()
        attach(before, name: "torch-off")

        torch.tap()
        XCTAssertTrue(waitForValue(torch, "on", timeout: 10),
                      "Torch was rejected. is the camera on the back?")
        Thread.sleep(forTimeInterval: 3)

        let after = XCUIScreen.main.screenshot()
        attach(after, name: "torch-on")

        let beforeBrightness = centreBrightness(before)
        let afterBrightness = centreBrightness(after)
        XCTAssertGreaterThan(afterBrightness, 8,
                             "Preview is black with the torch on (brightness \(afterBrightness))")
        XCTAssertGreaterThan(afterBrightness, beforeBrightness * 0.2,
                             "Preview lost too much brightness with the torch on " +
                             "(\(beforeBrightness) -> \(afterBrightness))")

        torch.tap()
        XCTAssertTrue(waitForValue(torch, "off", timeout: 10), "Torch did not turn off")

        Thread.sleep(forTimeInterval: 2)
        let restored = waitForLivePreview()
        attach(restored, name: "torch-off-restored")
        XCTAssertGreaterThan(centreBrightness(restored), beforeBrightness * 0.2,
                             "Preview did not return after turning the torch off")
    }

    /// PT: Ir para segundo plano encerra o estúdio e liberta a câmara.
    /// EN: Going to the background stops the studio and releases the camera.
    func testP2PStopsInBackground() throws {
        let app = makeApp()
        app.launch()
        openStudio(app)

        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()

        let alert = app.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 15), "Background notice did not appear")
        alert.buttons["OK"].tap()

        XCTAssertTrue(app.textFields["p2p.streamId"].waitForExistence(timeout: 15),
                      "Studio was not stopped after going to the background")
    }
}
