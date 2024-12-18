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

class ConfigurationHelper {
    private enum InvalidconfigurationError : Error {
        case missingParameter(parameter: String)
        case invalidUrl(url: String)
    }
    
    public static func createHaapiConfiguration(data : Dictionary<String, Any>) throws -> HaapiConfiguration {
        let scope = getStringArrayFromSpaceSeparated(data: data, configKey: "scope")
        let acrValues = getStringArrayFromSpaceSeparated(data: data, configKey: "acrValues")
        
        let validateTlsCertificate = data["validateTlsCertificate"] as? Bool ?? true
        let urlSession = URLSession(configuration: URLSessionConfiguration.haapi,
                                    delegate: validateTlsCertificate ? nil : TrustAllCertsDelegate(),
                                    delegateQueue: nil)
        let boundedTokenConfiguration = BoundedTokenConfiguration()
        let extraRequestParameters = getStringMap(data: data, configKey: "extraRequestParameters")
        let extraHttpHeaders = getStringMap(data: data, configKey: "extraHttpHeaders")
        
        return HaapiConfiguration(name: getStringOrDefault(data: data, configKey: "configurationName", defaultString: "HaapiModule"),
                                  clientId: try getStringOrThrow(data: data, configKey: "clientId"),
                                  baseURL: try getUrlOrThrow(data: data, configKey: "baseUri"),
                                  tokenEndpointURL: try getUrlOrThrow(data: data, configKey: "tokenEndpointUri"),
                                  authorizationEndpointURL: try getUrlOrThrow(data: data, configKey: "authorizationEndpointUri"),
                                  appRedirect: getStringOrDefault(data: data, configKey: "appRedirect", defaultString: "app:start"),
                                  httpHeadersProvider: { extraHttpHeaders },
                                  authorizationParametersProvider: { () -> OAuthAuthorizationParameters in OAuthAuthorizationParameters(scopes: scope,
                                                                                                                                        acrValues: acrValues,
                                                                                                                                        extraRequestParameters: extraRequestParameters) },
                                  isAutoRedirect: true,
                                  urlSession: urlSession,
                                  tokenBoundConfiguration: boundedTokenConfiguration)
        
    }
    
    private static func getUrlOrThrow(data: Dictionary<String, Any>, configKey: String) throws -> URL {
        let str = try getStringOrThrow(data: data, configKey: configKey)
        guard let url = URL(string: str) else { throw InvalidconfigurationError.invalidUrl(url: str) }
        return url
    }
    
    private static func getStringOrThrow(data: Dictionary<String, Any>, configKey: String) throws -> String {
        guard let str = data[configKey] else { throw InvalidconfigurationError.missingParameter(parameter: configKey) }
        return String(describing: str)
    }
    
    private static func getStringOrDefault(data: Dictionary<String, Any>, configKey: String, defaultString: String) -> String {
        guard let str = data[configKey] else { return defaultString }
        return String(describing: str)
    }
    
    private static func getStringArrayFromSpaceSeparated(data: Dictionary<String, Any>, configKey: String) -> [String] {
        return getStringOrDefault(data: data, configKey: configKey, defaultString: "")
            .split(separator: " ")
            .map { String($0) }
    }
    
    private static func getStringMap(data: Dictionary<String, Any>, configKey: String) -> Dictionary<String, String> {
        guard let map = data[configKey] as? [String: Any] else { return [String: String]() }
        var stringMap = [String: String]()
        for (key, value) in map {
            if let value = value as? String {
                stringMap[key] = value
            }
        }
        return stringMap
    }
}
