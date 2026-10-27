# Genesys Mock Android App

Mock app integrating the Genesys Cloud Messenger **Transport** SDK (`cloud.genesys:messenger-transport-mobile-sdk-android:2.16.0`).

- Open this folder in Android Studio (Ladybug or newer, JDK 17) and run the `app` configuration. Android Studio generates the Gradle wrapper.
- Defaults: deployment ID `your_deployment_id`, domain `mypurecloud.de`
  (edit `DEFAULT_*` in `app/build.gradle.kts`, or change them at runtime via **Genesys settings** on the login screen; persisted in SharedPreferences).
- Login (name, mobile, email) -> sent as custom attributes `name`, `mobileNumber`, `email`.
- Logout -> `clearConversation()` then `disconnect()`. Login always yields a fresh conversation: if a stored session is restored, it is cleared and reconnected.
