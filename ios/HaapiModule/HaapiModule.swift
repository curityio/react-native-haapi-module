//
// Copyright (C) 2024 Curity AB.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import Foundation
import IdsvrHaapiSdk

@objc(HaapiModule)
class HaapiModule: RCTEventEmitter {
    
    struct Promise {
        var resolve: RCTPromiseResolveBlock
        var reject: RCTPromiseRejectBlock
    }
    
    enum InitializationError: Error {
        case moduleNotLoaded(msg: String = "Configuration not created. Run load() on the module before starting a flow")
        case failedToCreateHaapiManager(_ rootCause: Error)
        case failedToCreateOAuthTokenManager(_ rootCause: Error)
    }
    
    private var currentRepresentation: HaapiRepresentation?
    private var haapiConfiguration: HaapiConfiguration?
    private var jsonDecoder: JSONDecoder = JSONDecoder()
    private var jsonEncoder: JSONEncoder = JSONEncoder()
    
    private var backingHaapiManager: HaapiManager?
    private var backingTokenManager: OAuthTokenManager?

    private var haapiManager: HaapiManager  {
        get throws {
            if (backingHaapiManager != nil) {
                return backingHaapiManager!
            }
            
            if(haapiConfiguration == nil) {
                throw InitializationError.moduleNotLoaded()
            }
            
            do {
                backingHaapiManager = try HaapiManager(haapiConfiguration: haapiConfiguration!)
                return backingHaapiManager!
                
            } catch {
                throw InitializationError.failedToCreateHaapiManager(error)
            }
        }
    }
    
    private var oauthTokenManager: OAuthTokenManager {
        get throws {
            if backingTokenManager != nil {
                return backingTokenManager!
            }
            
            guard haapiConfiguration != nil else { throw InitializationError.moduleNotLoaded() }
            
            backingTokenManager = OAuthTokenManager(oauthTokenConfiguration: haapiConfiguration!)
            return backingTokenManager!
        }
    }

    override init() {
        super.init()
        HaapiLogger.followUpTags = DriverFollowUpTag.allCases + SdkFollowUpTag.allCases
    }
    
    enum EventType : String, CaseIterable {
        case AuthenticationStep
        case AuthenticationSelectorStep
        case PollingStep
        case PollingStepResult
        case ContinueSameStep
        case TokenResponse
        case TokenResponseError
        case HaapiError
        case SessionTimedOut
        case IncorrectCredentials
        case StopPolling
        case ProblemRepresentation
    }
    
    override func supportedEvents() -> [String]! {
        return EventType.allCases.map { $0.rawValue }
    }
    
    @objc(load:resolver:rejecter:)
    func load(configuration: Dictionary<String, Any>,
              resolver resolve: @escaping RCTPromiseResolveBlock,
              rejecter reject: @escaping RCTPromiseRejectBlock) {
        let promise = Promise(resolve: resolve, reject: reject)
        
        do {
            haapiConfiguration = try ConfigurationHelper.createHaapiConfiguration(data: configuration)
            promise.resolve(true)
        } catch {
            rejectRequestWithError(description: "Could not configure module: \(error)", promise: promise)
        }
    }
    
    
    @objc(start:rejecter:)
    func start(resolver resolve: @escaping RCTPromiseResolveBlock,
               rejecter reject: @escaping RCTPromiseRejectBlock) {
        
        let promise = Promise(resolve: resolve, reject: reject)
        
        if(haapiConfiguration == nil) {
            rejectRequestWithError(description: "Configuration not created. Run load() on the module before starting a flow", promise: promise)
            return
        }
        
        if(backingHaapiManager != nil) {
            closeManagers()
        }
        
        do {
            try haapiManager.start(completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
        } catch {
            rejectRequestWithError(description: "Failed to start authentication flow. Error: \(error)", promise: promise)
            return
        }
    }
    
    @objc
    func submitForm(_ action: Dictionary<String, Any>,
                    parameters: Dictionary<String, Any>,
                    resolver resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {
        let promise = Promise(resolve: resolve, reject: reject)
        
        guard let model = action["model"] else {
            rejectRequestWithError(description: "", promise: promise)
            return
        }
        
        do {
            let actionObject = try JSONSerialization.data(withJSONObject: model)
            let formActionModel = try jsonDecoder.decode(FormActionModel.self, from: actionObject)
            try haapiManager.submitForm(formActionModel, parameters: parameters, completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
        } catch {
            rejectRequestWithError(description: "Failed to construct form to submit: \(error)", promise: promise)
        }
    }
    
    @objc
    func navigate(_ linkMap: Dictionary<String, Any>,
                  resolver resolve: @escaping RCTPromiseResolveBlock,
                  rejecter reject: @escaping RCTPromiseRejectBlock) {
        
        var mutableLinkMap = linkMap
        // should be optional in SDK
        if(mutableLinkMap["rel"] == nil) {
            mutableLinkMap["rel"] = "link"
        }
        
        let promise = Promise(resolve: resolve, reject: reject)
        
        do {
            let linkObject = try JSONSerialization.data(withJSONObject: mutableLinkMap)
            let link = try jsonDecoder.decode(Link.self, from: linkObject)
            try haapiManager.followLink(link, completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
        } catch {
            rejectRequestWithError(description: "Failed to construct link: \(error)", promise: promise)
        }
    }
    
    @objc
    func refreshAccessToken(_ refreshToken: String,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
        let promise = Promise(resolve: resolve, reject: reject)
        do {
            try oauthTokenManager.refreshAccessToken(with: refreshToken, completionHandler: { tokenResponse in
                self.handle(tokenResponse: tokenResponse, promise: promise)
            })
        } catch {
            
        }
    }
    
    @objc(logout:rejecter:)
    func logout(resolver resolve: RCTPromiseResolveBlock,
                rejecter reject: RCTPromiseRejectBlock) {
        closeManagers()
        resolve(true)
    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    private func closeManagers() {
        backingHaapiManager?.close()
        backingHaapiManager = nil
        backingTokenManager = nil
    }
    
    private func processHaapiResult(_ haapiResult: HaapiResult, promise: Promise) {
            
        switch haapiResult {
        case .representation(let representation):
            do {
                try process(haapiRepresentation: representation, promise: promise)
            } catch {
                rejectRequestWithError(description: "Failed to process HAAPI representation: \(error)", promise: promise)
            }
        case .problem(let problemRepresentation):
            process(problemRepresentation: problemRepresentation, promise: promise)
        case .error(let error):
            rejectRequestWithError(description: "Unknown error: " + error.localizedDescription, promise: promise)
        }
    }
    
    private func process(problemRepresentation: ProblemRepresentation, promise: Promise) {
        switch(problemRepresentation.type) {
        case .incorrectCredentialsProblem:
            resolveRequest(eventType: EventType.IncorrectCredentials, body: problemRepresentation, promise: promise)
        case .sessionAndAccessTokenMismatchProblem:
            resolveRequest(eventType: EventType.SessionTimedOut, body: problemRepresentation, promise: promise)
        default:
            resolveRequest(eventType: EventType.ProblemRepresentation, body: problemRepresentation, promise: promise)
        }
    }
    
    private func process(haapiRepresentation: HaapiRepresentation, promise: Promise) throws {
        switch(haapiRepresentation) {
        case is AuthenticatorSelectorStep:
            resolveRequest(eventType: EventType.AuthenticationSelectorStep, body: haapiRepresentation, promise: promise)
        case is InteractiveFormStep:
            resolveRequest(eventType: EventType.AuthenticationStep, body: haapiRepresentation, promise: promise)
        case is ContinueSameStep:
            resolveRequest(eventType: EventType.ContinueSameStep, body: haapiRepresentation, promise: promise)
        case let step as OAuthAuthorizationResponseStep:
            try handle(codeStep: step, promise: promise)
        case let step as PollingStep:
            try handle(pollingStep: step, promise: promise)
        default:
            rejectRequestWithError(description: "Unknown step", promise: promise)
        }
    }
    
    private func handle(pollingStep: PollingStep,
                        promise: Promise) throws {
        sendHaapiEvent(EventType.PollingStep, body: pollingStep, promise: promise)
        
        switch(pollingStep.pollingProperties.status) {
        case .pending:
            resolveRequest(eventType: EventType.PollingStepResult, body: pollingStep, promise: promise)
        case .failed:
            sendHaapiEvent(EventType.StopPolling, body: pollingStep, promise: promise)
            try submitModel(model: pollingStep.mainAction.model, promise: promise)
        case .done:
            sendHaapiEvent(EventType.StopPolling, body: pollingStep, promise: promise)
            try submitModel(model: pollingStep.mainAction.model, promise: promise)
        }
    }
    
    private func handle(tokenResponse: TokenResponse, promise: Promise) {
        switch(tokenResponse) {
        case .successfulToken(let successfulTokenResponse):
            let tokenResponse = SuccessTokenResponse(successfulTokenResponse)
            resolveRequest(eventType: EventType.TokenResponse, body: tokenResponse, promise: promise)
        case .errorToken(let errorTokenResponse):
            // Request succeeded, but with contents indicating an. Resolve with contents, so that frontend can act on it.
            resolveRequest(eventType: EventType.TokenResponseError, body: errorTokenResponse, promise: promise)
        case .error:
            rejectRequestWithError(description: "Failed to execute token request", promise: promise)
        }
    }
    
    private func submitModel(model: FormActionModel,
                             promise: Promise) throws {
        try haapiManager.submitForm(model, parameters: [:], completionHandler: { haapiResult in
            self.processHaapiResult(haapiResult, promise: promise)
        })
    }
    
    private func handle(codeStep: OAuthAuthorizationResponseStep, promise: Promise) throws {
        try oauthTokenManager.fetchAccessToken(with: codeStep.oauthAuthorizationResponseProperties.code!, dpop: haapiManager.dpop, completionHandler: { tokenResponse in
            self.handle(tokenResponse: tokenResponse, promise: promise)
        })
    }
    
    private func sendHaapiEvent(_ type: EventType, body: Codable, promise: Promise) {
        do {
            let encodedBody = try encodeObject(body)
            self.sendEvent(withName: type.rawValue, body: encodedBody)
        }
        catch {
            rejectRequestWithError(description: "Could not encode event as json. Error: \(error)", promise: promise)
        }
    }
    
    private func encodeObject(_ object: Codable) throws -> Any {
        do {
            let jsonData = try jsonEncoder.encode(object)
            return try JSONSerialization.jsonObject(with: jsonData)
        }
        catch {
            throw NSError()
        }
    }
    
    private func rejectRequestWithError(description: String, promise: Promise) {
        sendHaapiEvent(EventType.HaapiError, body: ["error": "HaapiError", "error_description": description], promise: promise)
        promise.reject("HaapiError", description, nil)
        closeManagers()
    }
    
    private func resolveRequest(eventType: EventType, body: Codable, promise: Promise) {
        do {
            let encodedBody = try encodeObject(body)
            promise.resolve(encodedBody)
            self.sendEvent(withName: eventType.rawValue, body: encodedBody)
        }
        catch {
            rejectRequestWithError(description: "Could not encode response as json. Error: \(error)", promise: promise)
        }
    }
    
    private struct SuccessTokenResponse : Codable {
        var accessToken: String
        var refreshToken: String?
        var idToken: String?
        var scope: String
        var expiresIn: Int
        
        init(_ tokenResponse : SuccessfulTokenResponse) {
            self.accessToken = tokenResponse.accessToken
            self.idToken = tokenResponse.idToken
            self.refreshToken = tokenResponse.refreshToken
            self.scope = tokenResponse.scope ?? ""
            self.expiresIn = tokenResponse.expiresIn
        }
    }
    
}
