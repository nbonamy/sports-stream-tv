import Capacitor
import WebKit

class SportsViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(SportsHttpPlugin())
        webView?.scrollView.bounces = false
        webView?.isOpaque = false
    }
    override func webViewConfiguration(for instanceConfiguration: InstanceConfiguration) -> WKWebViewConfiguration {
        let config = super.webViewConfiguration(for: instanceConfiguration)
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        return config
    }
    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
}
