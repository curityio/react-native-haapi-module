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
import AuthenticationServices
import IdsvrHaapiSdk
import OSLog

protocol WebAuthnRegistrationResponseHandler {
    var onSuccess: () -> Void { get }
    var onError: (_ error: String) -> Void { get }
}

public enum WebAuthnClientOperationError : Error {
    case missingParameter(message: String)
    case failedOperation(message: String)
    case userCancelled
}

@available(iOS 15.0, *)
typealias WebAuthnRegistrationCompletionHandler = (Result<ASAuthorizationPublicKeyCredentialRegistration, WebAuthnClientOperationError>) -> Void

@available(iOS 15.0, *)
typealias WebAuthnAuthenticationCompletionHandler = (Result<ASAuthorizationPublicKeyCredentialAssertion, WebAuthnClientOperationError>) -> Void

@available(iOS 15.0, *)
class WebAuthnClientOperationHandler : NSObject {
    private let logger = Logger()
    
    @available(iOS 15.0, *)
    func register(operation: WebAuthnRegistrationClientOperationStep, completionHandler: @escaping WebAuthnRegistrationCompletionHandler) {
        var registrationRequest:ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest
        do {
            registrationRequest = try buildCredentialRegistrationRequest(operation: operation)
        }
        catch let error {
            completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: error.localizedDescription)))
            return
        }
        
        logger.info("Starting passkey registration")
        
        let authController = ASAuthorizationController(authorizationRequests: [registrationRequest])
        
        Task {
            do {
                guard let credential = try await WebAuthnClientOperationDelegate().performRequestsFor(authorizationController: authController) as? ASAuthorizationPublicKeyCredentialRegistration else {
                    completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: "Did not receive a credential after registration request")))
                    return
                }
                completionHandler(.success(credential))
            } catch WebAuthnClientOperationError.userCancelled {
                logger.debug("User cancelled the registration")
                completionHandler(.failure(WebAuthnClientOperationError.userCancelled))
            } catch {
                logger.warning("Failed to perform assertion operation: \(error.localizedDescription)")
                completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: error.localizedDescription)))
            }
            
        }
    }
    
    func authenticate(operation: WebAuthnAuthenticationClientOperationStep, completionHandler: @escaping WebAuthnAuthenticationCompletionHandler) {
        var assertionRequest: ASAuthorizationPlatformPublicKeyCredentialAssertionRequest
        do {
            assertionRequest = try buildAssertionRequest(operation: operation)
        }
        catch {
            completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: error.localizedDescription)))
            return
        }
        
        logger.debug("Starting passkey authentication")
        
        let authController = ASAuthorizationController(authorizationRequests: [assertionRequest])
        Task {
            do {
                guard let assertion = try await WebAuthnClientOperationDelegate().performRequestsFor(authorizationController: authController) as? ASAuthorizationPublicKeyCredentialAssertion else {
                    completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: "Did not receive an assertion after assertion request")))
                    return
                }
                completionHandler(.success(assertion))
            } catch WebAuthnClientOperationError.userCancelled {
                logger.debug("User cancelled the authentication")
                completionHandler(.failure(WebAuthnClientOperationError.userCancelled))
            } catch {
                logger.warning("Failed to perform assertion operation: \(error.localizedDescription)")
                completionHandler(.failure(WebAuthnClientOperationError.failedOperation(message: error.localizedDescription)))
            }
            
        }
    }

    private func buildAssertionRequest(operation: WebAuthnAuthenticationClientOperationStep) throws -> ASAuthorizationPlatformPublicKeyCredentialAssertionRequest {
            
        let credentialOptions = operation.actionModel.credentialOptions
        guard let challenge = credentialOptions.challengeData else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get challenge from server credential request") }
        guard let rpId = credentialOptions.relyingPartyId else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get rpId from server credential request") }
        guard let userVerification = credentialOptions.userVerificationPreference else { throw WebAuthnClientOperationError.missingParameter(message: "Failed to get userVerification from server credential request") }
        
        let platformProvider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        
        let request = platformProvider.createCredentialAssertionRequest(challenge: challenge)
        request.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: userVerification)

        let allowedCredentials: [ASAuthorizationPlatformPublicKeyCredentialDescriptor] =
        credentialOptions.platformAllowCredentials?.map { cred in
            return ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: cred.credentialID)
        } ?? []
        
        request.allowedCredentials = allowedCredentials
        
        return request
    }
    
    private func buildCredentialRegistrationRequest(operation: WebAuthnRegistrationClientOperationStep) throws -> ASAuthorizationPlatformPublicKeyCredentialRegistrationRequest {
            
        guard let createCredentialOptions = operation.actionModel.platformOptions else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get credential request from server") }
        guard let challenge = createCredentialOptions.challengeData else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get challenge from server credential request") }
        guard let rpId = createCredentialOptions.relyingPartyId else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get rpId from server credential request") }
        guard let userName = createCredentialOptions.userName else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get userName from server credential request") }
        guard let userId = createCredentialOptions.userIdData else {throw WebAuthnClientOperationError.missingParameter(message: "Failed to get userId from server credential request") }
        
        let platformProvider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        
        return platformProvider.createCredentialRegistrationRequest(challenge: challenge, name: userName, userID: userId)
    }
    

}

@available(iOS 15.0, *)
class WebAuthnClientOperationDelegate : NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let logger = Logger()
    private var continuation: CheckedContinuation<ASAuthorizationCredential, Error>?
    
    override init() {super.init()}

    func performRequestsFor(authorizationController: ASAuthorizationController) async throws -> ASAuthorizationCredential {
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            authorizationController.delegate = self
            authorizationController.presentationContextProvider = self
            authorizationController.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        switch authorization.credential {
        case let credentialRegistration as ASAuthorizationPlatformPublicKeyCredentialRegistration:
            logger.info("A new key was registered: \(credentialRegistration)")
            continuation?.resume(returning: credentialRegistration)
            break
        case let assertion as ASAuthorizationPlatformPublicKeyCredentialAssertion:
            logger.debug("A key was used to authenticate: \(assertion)")
            continuation?.resume(returning: assertion)
            break
        default:
            logger.warning("Received unknown authorization type")
            continuation?.resume(throwing: WebAuthnClientOperationError.failedOperation(message: "Received unknown authorization result"))
        }
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        switch ((error as? ASAuthorizationError)?.code) {
        case .canceled:
            continuation?.resume(throwing: WebAuthnClientOperationError.userCancelled)
            break;
        default:
            continuation?.resume(throwing: WebAuthnClientOperationError.failedOperation(message: error.localizedDescription))
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return UIApplication.shared.windows.first!
    }
}
