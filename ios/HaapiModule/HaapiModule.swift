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
    private var haapiManager: HaapiManager?
    private var oauthTokenManager: OAuthTokenManager?
    private var currentRepresentation: HaapiRepresentation?
    private var haapiConfiguration: HaapiConfiguration?
    private val jsonDecoder: JSONDecoder
    
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
        
        do {
            haapiManager = try HaapiManager(haapiConfiguration: haapiConfiguration!)
            oauthTokenManager = OAuthTokenManager(oauthTokenConfiguration: haapiConfiguration!)
        } catch {
            rejectRequestWithError(description: "Failed to create haapi manager. Error: \(error)", promise: promise)
            return
        }
        
        haapiManager?.start(completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
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
            haapiManager?.submitForm(formActionModel, parameters: parameters, completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
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
            haapiManager?.followLink(link, completionHandler: { haapiResult in self.processHaapiResult(haapiResult, promise: promise) })
        } catch {
            rejectRequestWithError(description: "Failed to construct link: \(error)", promise: promise)
        }
    }
    
    @objc
    func refreshAccessToken(_ refreshToken: String,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
        let promise = Promise(resolve: resolve, reject: reject)
        oauthTokenManager?.refreshAccessToken(with: refreshToken, completionHandler: { tokenResponse in
            self.handle(tokenResponse: tokenResponse, promise: promise)
        })
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
        haapiManager?.close()
        oauthTokenManager = nil
    }
    
    private func processHaapiResult(_ haapiResult: HaapiResult, promise: Promise) {
        switch haapiResult {
        case .representation(let representation):
            process(haapiRepresentation: representation, promise: promise)
        case .problem(let problemRepresentation):
            process(problemRepresentation: problemRepresentation, promise: promise)
        case .error(let error):
            rejectRequestWithError(description: "Unknown error: " + error.localizedDescription, promise: promise)
        }
    }
    
    private func process(problemRepresentation: ProblemRepresentation, promise: Promise) {
        switch(problemRepresentation.type) {
        case .incorrectCredentialsProblem:
            sendHaapiEvent(EventType.IncorrectCredentials, body: problemRepresentation)
        case .sessionAndAccessTokenMismatchProblem:
            sendHaapiEvent(EventType.SessionTimedOut, body: problemRepresentation)
        default:
            sendHaapiEvent(EventType.ProblemRepresentation, body: problemRepresentation)
        }
    }
    
    private func process(haapiRepresentation: HaapiRepresentation, promise: Promise) {
        switch(haapiRepresentation) {
        case is AuthenticatorSelectorStep:
            resolveRequest(eventType: EventType.AuthenticationSelectorStep, body: haapiRepresentation, promise: promise)
        case is InteractiveFormStep:
            resolveRequest(eventType: EventType.AuthenticationStep, body: haapiRepresentation, promise: promise)
        case is ContinueSameStep:
            resolveRequest(eventType: EventType.ContinueSameStep, body: haapiRepresentation, promise: promise)
        case let step as OAuthAuthorizationResponseStep:
            handle(codeStep: step, promise: promise)
        case let step as PollingStep:
            handle(pollingStep: step, promise: promise)
        default:
            rejectRequestWithError(description: "Unknown step", promise: promise)
        }
    }
    
    private func handle(pollingStep: PollingStep,
                        promise: Promise) {
        sendHaapiEvent(EventType.PollingStep, body: pollingStep)
        
        switch(pollingStep.pollingProperties.status) {
        case .pending:
            resolveRequest(eventType: EventType.PollingStepResult, body: pollingStep, promise: promise)
        case .failed:
            sendHaapiEvent(EventType.StopPolling, body: pollingStep)
            submitModel(model: pollingStep.mainAction.model, promise: promise)
        case .done:
            submitModel(model: pollingStep.mainAction.model, promise: promise)
        }
    }
    
    private func handle(tokenResponse: TokenResponse, promise: Promise) {
        switch(tokenResponse) {
        case .successfulToken(let successfulTokenResponse):
            let tokenResponse = self.mapTokenResponse(successfulTokenResponse)
            resolveRequest(eventType: EventType.TokenResponse, body: tokenResponse, promise: promise)
        case .errorToken(let errorTokenResponse):
            // Request succeeded, but with contents indicating an. Resolve with contents, so that frontend can act on it.
            resolveRequest(eventType: EventType.TokenResponseError, body: errorTokenResponse, promise: promise)
        case .error:
            rejectRequestWithError(description: "Failed to execute token request", promise: promise)
            self.sendHaapiError(description: "Failed to execute token request")
        }
    }
    
    private func submitModel(model: FormActionModel,
                             promise: Promise) {
        haapiManager?.submitForm(model, parameters: [:], completionHandler: { haapiResult in
            self.processHaapiResult(haapiResult, promise: promise)
        })
    }
    
    private func handle(codeStep: OAuthAuthorizationResponseStep, promise: Promise) {
        oauthTokenManager?.fetchAccessToken(with: codeStep.oauthAuthorizationResponseProperties.code!, completionHandler: { tokenResponse in
            self.handle(tokenResponse: tokenResponse, promise: promise)
        })
    }
    
    private func mapTokenResponse(_ succesfulTokenResponse: SuccessfulTokenResponse) -> Dictionary<String, String> {
        var tokenResponse = ["accessToken": succesfulTokenResponse.accessToken]
        if (succesfulTokenResponse.idToken != nil) {
            tokenResponse["idToken"] = succesfulTokenResponse.idToken
        }
        if (succesfulTokenResponse.refreshToken != nil) {
            tokenResponse["refreshToken"] = succesfulTokenResponse.refreshToken
        }
        tokenResponse["scope"] = succesfulTokenResponse.scope ?? ""
        return tokenResponse
    }
    
    private func sendHaapiEvent(_ type: EventType, body: Codable) {
        do {
            let encodedBody = try encodeObject(body)
            self.sendEvent(withName: type.rawValue, body: encodedBody)
        }
        catch {
            self.sendHaapiError(description: "Could not encode event as json. Error: \(error)");
        }
    }
    
    private func sendProblemEvent(_ problem: ProblemRepresentation) {
        sendHaapiEvent(EventType.ProblemRepresentation, body: problem)
    }
    
    private func sendHaapiError(description: String) {
        sendHaapiEvent(EventType.HaapiError, body: ["error": "HaapiError", "error_description": description])
    }
    
    private func encodeObject(_ object: Codable) throws -> Any {
        do {
            let jsonData = try jsonEncoder.encode(object)
            let jsonString = String(bytes:jsonData, encoding: String.Encoding.utf8)
            return try JSONSerialization.jsonObject(with: jsonData)
        }
        catch {
            throw NSError()
        }
    }
    
    private func rejectRequestWithError(description: String, promise: Promise) {
        sendHaapiError(description: description)
        promise.reject("HaapiError", description, nil)
        closeManagers()
    }
    
    private func resolveRequest(eventType: EventType, body: Codable, promise: Promise) {
        sendHaapiEvent(eventType, body: body)
        promise.resolve(body)
    }
    
}
