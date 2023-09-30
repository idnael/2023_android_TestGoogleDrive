This is a basic Android demo app to access Google Drive using Googleapis.

# GCP project creation

To access Google Drive, you will need to setup a Google Cloud Platform project,
using your developer account.
With it, any user will be able to acess their own GDrive files.

In GCP Console, go to "Api & Services" / "OAUTH CONSENT SCREEN":

- app name: TestGDrive_gcp
- User support email: ...
- User type: internal vs external - it only allows me to choose EXTERNAL
- App domain: nothing
- Scopes? nothing  ("Scopes express the permissions you request users to authorize for your app and allow your project to access specific types of private user data from their Google Account.")
- Test users: Since this is a TEST app, you need to give the users that will be able to use it

Now go to "Api & Services" / CREATE CREDENCIAL,
and choose Create a credential / OAUTH CLIENT ID:

- app type: android
- name: Android client 1
- package name: your android package
- SHA1 - you can get it from Android Studio. See https://stackoverflow.com/questions/27609442/how-to-get-the-sha-1-fingerprint-certificate-in-android-studio-for-debug-mode

OAuth client created! He will give the client ID, but we don't need to use it

Finally, go to ENABLE APIS AND SERVICES, and Enable the Google Drive API - "The Google Drive API allows clients to access resources from Google Drive."

# Android project

These are the steps:
- Get required permissions, and create Drive and Credential objects. See `initDrive` function
- Launch an Intent to allow the user to choose an account.
  A android system dialog will show up. See `chooseAccountLauncher`.
- Make Drive api calls
- If we get an `UserRecoverableAuthIOException`,
  we get the intent property from the exception object, and launch it.
  This should happen the first time the user uses the app.
  A android system dialog will show up, asking the user to allow
  our GCP app to access his GDrive data.
  See `userRecoverableAuthLauncher`


For more information about the Google Drive API (This is a Java API, not Android specific): https://developers.google.com/drive/api/guides/about-sdk

Note: I couldn't find where the trick of getting the Intent from
the auth exception is documented. I learned it from other examples
and posts I found online.

# About getting the GDrive root folder

I tried several queries, none worked:
```
  q ="'' in parents"  // "File not found: ."
  q ="null in parents"  // bad request "Invalid Value"
  q = "name = 'My files'" // gives 0 results... but my root folder is named like this!
  q = "'drive' in parents"  -- error
  q="not parents" // Unknown Error.
  q="parents = null" // invalid value
  etc
```

The only solution I found was to get a random file, and follow its ancestors until getting a folder with null parent. That should be the top folder. But this process involves several google api calls. And I don't know if it is guaranteed to always work.

See here: https://stackoverflow.com/questions/44090344/retrieving-top-level-files-in-shared-with-me-from-google-drive-v3-java-api/77203855#77203855
