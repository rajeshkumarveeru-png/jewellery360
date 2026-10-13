# Jewellery360 mobile (Expo SDK 57)

For shop staff and owners: dashboard, billing (search or camera-scan tags, server-priced totals, split payment), bill
history and payments, customers (dues, collect, history), stock lookup with piece history, today's gold rates, orders and
repairs status, and the day-close cash count. Stock entry, purchases, reports, settings and users stay on the web app.

The app uses the same API and permissions as the web app; the server checks every request. The platform administrator
(APP_ADMIN) works across companies and should use the web app.

## Run
```
cp .env.example .env        # set EXPO_PUBLIC_API_URL to http://<your PC's LAN IP>:8080/api
npm ci
npx expo start              # scan the QR code with Expo Go, or press a / i / w
```
The phone and the PC must be on the same network, and the backend must allow the origin when using the web build
(APP_CORS_ALLOWED_ORIGINS).

## Checks
```
npx tsc --noEmit
npx expo-doctor
```
