# Jewellery360

Multi-tenant jewellery ERP/POS foundation based on the BizManager user/security model.

## Hierarchy
App Admin -> Company/Tenant -> Company Admin -> maximum 10 Company Users.

Company users can have roles:
MANAGER, CASHIER, SALESMAN, INVENTORY_MANAGER, ACCOUNTANT, VIEWER.

Password reset:
- WhatsApp OTP
- Admin approval

Customer authentication is intentionally separated from internal AppUser.

## Stack
Java 21, Spring Boot 3.5.4, Gradle 8.14.3, Spring Security, JWT, PostgreSQL, Liquibase.
Frontend: React + TypeScript + Vite.

## Run
1. Create PostgreSQL database `jewellery360`.
2. Set environment variables:
   DB_URL=jdbc:postgresql://localhost:5432/jewellery360
   DB_USERNAME=postgres
   DB_PASSWORD=postgres
   JWT_SECRET=replace-with-a-long-secret-at-least-32-bytes
3. `cd backend && gradlew bootRun`
4. `cd frontend && npm install && npm run dev`

The WhatsApp service uses Meta Graph API. Configure the tenant properties:
WHATSAPP_ENABLED, WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_GRAPH_VERSION.
Never commit a real access token.

# to add wrapper file

gradle wrapper --gradle-version 8.14.3

# DataBase Info

DROP DATABASE jewellery360;

CREATE USER jewellery360 WITH PASSWORD 'jewellery360';

CREATE DATABASE jewellery360 OWNER jewellery360;

GRANT ALL PRIVILEGES ON DATABASE jewellery360 TO jewellery360;


& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost

\c jewellery360
\c postgres

ALTER DATABASE jewellery360 OWNER TO jewellery360;

ALTER SCHEMA public OWNER TO jewellery360;

GRANT USAGE, CREATE ON SCHEMA public TO jewellery360;

# mobile build

cd mobile
npm install
npx expo start

Then in PowerShell:

cd D:\source_code\BizManager-Full-Stack-Latest\BizManager-Full-Stack\mobile

Remove-Item -Recurse -Force node_modules -ErrorAction SilentlyContinue
Remove-Item package-lock.json -ErrorAction SilentlyContinue

cd D:\source_code\BizManager-Full-Stack-Latest\BizManager-Full-Stack\mobile

npm install

npx expo install --fix

npx expo-doctor

npx expo config --json


npm install -g eas-cli
eas --version
eas login
eas whoami
eas build:configure
npx expo-doctor
npx expo install expo-dev-client
eas build --platform android --profile development

npx expo start --dev-client

mkdir certs
winget install FiloSottile.mkcert
mkcert -version
mkcert -install
mkcert -key-file .\certs\192.168.1.9-key.pem -cert-file .\certs\192.168.1.9.pem 192.168.1.9 localhost 127.0.0.1

#### scanneer

npm install @zxing/browser

## Off the firewall

Get-NetFirewallProfile | Select-Object Name, Enabled

Set-NetFirewallProfile -Profile Domain,Public,Private -Enabled False

New-NetFirewallRule `
  -DisplayName "Biz360 Spring Boot 8080" `
-Direction Inbound `
  -Protocol TCP `
-LocalPort 8080 `
  -Action Allow `
-Profile Private

Get-NetFirewallRule -DisplayName "Biz360 Spring Boot 8080"

Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

Get-NetConnectionProfile

Get-NetFirewallRule -DisplayName "Biz360 Spring Boot 8080" |
Format-Table DisplayName,Enabled,Profile,Direction,Action

ipconfig

# Remove NPM catch

Remove-Item -Recurse -Force node_modules
Remove-Item -Recurse -Force dist -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .vite -ErrorAction SilentlyContinue
npm cache clean --force
npm install
npm run build
npm run dev

In Chrome:

Ctrl + Shift + R

or open DevTools → hold the reload button → Empty Cache and Hard Reload.

## Restore backup Dump
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" `
  -l `
"D:\Biz360\backups\bizmanager_2026-09-04.backup"

## To make as destop Application
cd D:\source_code\BIZ360
New-Item -ItemType Directory -Force -Path desktop\electron

npm install --save-dev electron electron-builder
npm run electron
npm run electron:win
npm run electron:portable

New-Item -ItemType Directory -Force -Path desktop\backend
New-Item -ItemType Directory -Force -Path desktop\runtime

Copy-Item .\build\libs\*.jar D:\source_code\BIZ360\desktop\backend\biz360.jar

notepad "$env:APPDATA\BIZ360\logs\backend.log"

# to .exe
cd D:\source_code\BIZ360\desktop
npm install --save-dev electron-builder@26.0.12
npx electron-builder --version
npm run dist