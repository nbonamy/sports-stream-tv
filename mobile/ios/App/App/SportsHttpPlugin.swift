import Foundation
import Capacitor

@objc(SportsHttpPlugin)
public final class SportsHttpPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "SportsHttpPlugin"
    public let jsName = "SportsHttp"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "request", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancel", returnType: CAPPluginReturnPromise)
    ]
    private let queue = DispatchQueue(label: "sports.http")
    private var requests: [String: SportsRequest] = [:]

    @objc func request(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), id.count <= 100,
              let raw = call.getString("url"), raw.count <= 16384,
              let url = URL(string: raw),
              let limit = call.getInt("limit"), (1...33554432).contains(limit),
              let headers = call.getObject("headers") as? [String: String] else {
            call.reject("Invalid request"); return
        }
        queue.async {
            guard self.requests[id] == nil, self.requests.count < 64 else {
            call.reject("Invalid request"); return
            }
            let request = SportsRequest(url: url, headers: headers, limit: limit, queue: self.queue) { result in
                self.requests.removeValue(forKey: id)
                switch result {
                case .success(let response): call.resolve(response)
                case .failure: call.reject("Source unavailable")
                }
            }
            self.requests[id] = request
            request.start()
        }
    }
    @objc func cancel(_ call: CAPPluginCall) {
        let id = call.getString("id") ?? ""
        queue.async {
            self.requests[id]?.cancel()
            call.resolve()
        }
    }
}

private final class SportsRequest: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let url: URL
    private let headers: [String: String]
    private let limit: Int
    private let queue: DispatchQueue
    private var finish: ((Result<[String: Any], Error>) -> Void)?
    private var session: URLSession?
    private var task: URLSessionDataTask?
    private var deadline: DispatchWorkItem?
    private var body = Data()
    private var redirects = 0
    private var finalURL: URL?
    private var status = 0
    private var failed = false
    init(url: URL, headers: [String: String], limit: Int, queue: DispatchQueue,
         finish: @escaping (Result<[String: Any], Error>) -> Void) {
        self.url = url; self.headers = headers; self.limit = limit; self.queue = queue; self.finish = finish
    }
    func start() {
        guard Self.publicURL(url) else { complete(false); return }
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 18
        config.urlCache = nil
        config.httpCookieStorage = nil
        let delegateQueue = OperationQueue()
        delegateQueue.maxConcurrentOperationCount = 1
        delegateQueue.underlyingQueue = queue
        let session = URLSession(configuration: config, delegate: self, delegateQueue: delegateQueue)
        self.session = session
        var request = URLRequest(url: url)
        request.allHTTPHeaderFields = headers
        task = session.dataTask(with: request)
        let deadline = DispatchWorkItem { [weak self] in self?.cancel() }
        self.deadline = deadline
        queue.asyncAfter(deadline: .now() + 18, execute: deadline)
        task?.resume()
    }
    func cancel() { failed = true; task?.cancel(); complete(false) }
    private func complete(_ success: Bool) {
        guard let finish = finish else { return }
        self.finish = nil
        deadline?.cancel()
        session?.invalidateAndCancel()
        session = nil
        if success, let finalURL = finalURL {
            finish(.success(["url": finalURL.absoluteString, "status": status, "data": body.base64EncodedString()]))
        } else { finish(.failure(URLError(.cannotLoadFromNetwork))) }
        body.removeAll()
    }
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        redirects += 1
        guard redirects <= 5, let next = request.url, Self.publicURL(next), finish != nil else {
            failed = true; completionHandler(nil); return
        }
        var nextRequest = request
        nextRequest.allHTTPHeaderFields = headers
        completionHandler(nextRequest)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              response.expectedContentLength <= Int64(limit), finish != nil else {
            failed = true; completionHandler(.cancel); return
        }
        finalURL = http.url; status = http.statusCode
        completionHandler(.allow)
    }
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard finish != nil else { return }
        guard body.count + data.count <= limit else { cancel(); return }
        body.append(data)
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        complete(error == nil && !failed)
    }
    // Check initial and redirect DNS answers. TLS validation remains URLSession's default.
    private static func publicURL(_ url: URL) -> Bool {
        guard url.scheme == "https", url.user == nil, url.password == nil, let host = url.host,
              host.contains("."), !host.hasSuffix(".local"), !host.hasSuffix(".internal"), !host.hasSuffix(".localhost") else { return false }
        var hints = addrinfo()
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &result) == 0, let first = result else { return false }
        defer { freeaddrinfo(first) }
        var current: UnsafeMutablePointer<addrinfo>? = first
        while let address = current {
            let info = address.pointee
            if info.ai_family == AF_INET {
                let ip = info.ai_addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { UInt32(bigEndian: $0.pointee.sin_addr.s_addr) }
                let a = ip >> 24, b = (ip >> 16) & 255
                if a == 0 || a == 10 || a == 127 || a >= 224 || (a == 169 && b == 254) ||
                    (a == 172 && (16...31).contains(b)) || (a == 192 && (b == 168 || b == 0)) ||
                    (a == 100 && (64...127).contains(b)) || (a == 198 && (18...19).contains(b)) { return false }
            } else if info.ai_family == AF_INET6 {
                let bytes = info.ai_addr.withMemoryRebound(to: sockaddr_in6.self, capacity: 1) { ptr in
                    withUnsafeBytes(of: ptr.pointee.sin6_addr) { Array($0) }
                }
                if bytes[0] & 0xe0 != 0x20 { return false }
            } else { return false }
            current = info.ai_next
        }
        return true
    }
}
