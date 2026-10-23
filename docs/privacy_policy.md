# Privacy Policy — Sign Korean (draft)

_Draft for the Phase 21 release preparation. The publisher name and contact address are filled in when the Play Console account is set up. This text must match the Play data safety form._

## What stays on your device

- **Learning record** (which cards you marked "I know" or "Don't know"), the current practice session, **bookmarks** and **trips** are stored only on your device. They are not uploaded and are not backed up to other devices.
- You can delete them in Settings: "Reset learning record" and "Clear bookmarks". Deleting a trip removes it from the device.

## Location

- The app reads your location **only when you tap "Use my current location"** while choosing a day's start point, after an explanation and the system permission prompt.
- It reads the location once. It does not track you and does not use background location.
- The location is used on the device to offer a start point. It is **saved only if you apply it as the day's start**, and then only inside that trip on your device. If you do, that start point is sent to the relay server as the start of the route whenever routes for that day are calculated. It is never written to logs.

## Network requests

- **Walking and transit routes** come from the Sign Korean relay server, which asks Kakao Map. Route requests contain the start and end coordinates of that leg and the travel mode. They do not contain your name, an account or a device identifier; like any web request, they reveal your IP address to the relay server. The relay server does not store request contents: it logs only the request path and result code.
- **Reviews** are not shown inside the app. "View reviews on Kakao Map" sends the place's Korean name and the place's coordinates (not yours) to the relay server to find that place's Kakao Map page, then opens Kakao Map in your browser or the Kakao Map app. Kakao's own privacy policy applies there.
- **Place photos** are downloaded from the Korea Tourism Organization (TourAPI) and Wikimedia Commons. These services receive normal web requests (IP address, user agent) under their own privacy policies.
- Provider secret keys are kept on the relay server, not in the app.

## Purchases

- The one-time upgrade is processed by **Google Play**. The app receives whether the purchase is complete and a purchase token it uses to confirm the purchase with Google Play; it never sees your payment details.

## Children

- The app is not directed at children under 13 and does not knowingly collect their information.

## Changes and contact

- Changes to this policy will be posted with the app update that needs them.
- Contact: (to be added with the Play Console account).
