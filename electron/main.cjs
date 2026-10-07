const {
    app,
    BrowserWindow,
    dialog
} = require('electron')

const path = require('path')
const fs = require('fs')
const { spawn } = require('child_process')
const http = require('http')

const APP_ID = '@@APPID@@'
const APP_NAME = '@@NAME@@'
const COMPANY_NAME = 'R2Tech Solutions'

const BACKEND_PORT = 8080
const BACKEND_URL = `http://localhost:${BACKEND_PORT}/`

let mainWindow = null
let loadingWindow = null
let backendProcess = null
let backendLogStream = null
let backendErrorLogStream = null

/*
 * ============================================================
 * BIZ360 DIRECTORY
 * ============================================================
 *
 * Packaged portable application:
 *
 *   C:\BIZ360\
 *   D:\BIZ360\
 *   E:\BIZ360\
 *
 * The application can be moved to another drive/folder.
 *
 * Development:
 *
 *   <project-root>\
 *
 * No hard-coded customer path is used.
 */
function getBiz360Directory() {
    if (app.isPackaged) {
        if (process.env.PORTABLE_EXECUTABLE_DIR) {
            return process.env.PORTABLE_EXECUTABLE_DIR
        }

        return path.dirname(process.execPath)
    }

    return path.resolve(__dirname, '..')
}

const BIZ360_DIR = getBiz360Directory()

/*
 * ============================================================
 * DIRECTORY STRUCTURE
 * ============================================================
 *
 * BIZ360/
 *
 * ├── BIZ360.exe
 * ├── runtime/
 * │   └── java/
 * │       └── bin/
 * │           └── java.exe
 * │
 * ├── lib/
 * │   └── biz360.jar
 * │
 * ├── config/
 * │   ├── application.properties
 * │   └── logback-spring.xml
 * │
 * ├── logs/
 * └── backup/
 *
 */

const RUNTIME_DIR = path.join(
    BIZ360_DIR,
    'runtime'
)

const JAVA_HOME = path.join(
    RUNTIME_DIR,
    'java'
)

const JAVA_EXE = path.join(
    JAVA_HOME,
    'bin',
    'java.exe'
)

const LIB_DIR = path.join(
    BIZ360_DIR,
    'lib'
)

/*
 * The backend jar is whatever single .jar sits in lib\ (smart-billing.jar, biz360.jar, jewellery360.jar ...),
 * so the same main.cjs works for every product.
 */
function findBackendJar() {
    try {
        const jars = fs.readdirSync(LIB_DIR)
            .filter(name => name.toLowerCase().endsWith('.jar'))
            .sort()
        if (jars.length > 0) {
            return path.join(LIB_DIR, jars[0])
        }
    } catch (error) {
        /* reported by validateInstallation() */
    }
    return path.join(LIB_DIR, 'backend.jar')
}

const BACKEND_JAR = findBackendJar()

const CONFIG_DIR = path.join(
    BIZ360_DIR,
    'config'
)

const APPLICATION_PROPERTIES = path.join(
    CONFIG_DIR,
    'application.properties'
)

const LOG_CONFIG = path.join(
    CONFIG_DIR,
    'logback-spring.xml'
)

const LOG_DIR = path.join(
    BIZ360_DIR,
    'logs'
)

const BACKUP_DIR = path.join(
    BIZ360_DIR,
    'backup'
)

app.setAppUserModelId(APP_ID)

/*
 * ============================================================
 * COMMON HELPERS
 * ============================================================
 */

function ensureDirectory(directory) {
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, {
            recursive: true
        })
    }
}

function writeElectronLog(message) {
    try {
        ensureDirectory(LOG_DIR)

        const logFile = path.join(
            LOG_DIR,
            'electron.log'
        )

        const timestamp =
            new Date().toISOString()

        fs.appendFileSync(
            logFile,
            `[${timestamp}] ${message}\n`,
            'utf8'
        )
    } catch (error) {
        console.error(
            'Unable to write Electron log:',
            error
        )
    }
}

/*
 * ============================================================
 * LOADING WINDOW
 * ============================================================
 */

const loadingHtml = `
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">

<style>

html,
body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: transparent;
    font-family:
        "Segoe UI",
        Arial,
        sans-serif;
}

body {
    display: flex;
    align-items: center;
    justify-content: center;
}

.loading-card {
    width: 450px;
    min-height: 300px;

    box-sizing: border-box;

    border-radius: 22px;

    background:
        linear-gradient(
            145deg,
            #ffffff 0%,
            #f8fafc 50%,
            #eef2f7 100%
        );

    box-shadow:
        0 20px 60px rgba(15, 23, 42, 0.20),
        0 4px 18px rgba(15, 23, 42, 0.10);

    border: 1px solid rgba(148, 163, 184, 0.30);

    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    padding: 32px;
}

.logo {
    width: 78px;
    height: 78px;

    border-radius: 18px;

    background:
        linear-gradient(
            135deg,
            #2563eb,
            #1d4ed8
        );

    color: white;

    display: flex;
    align-items: center;
    justify-content: center;

    font-size: 34px;
    font-weight: 800;

    letter-spacing: -2px;

    box-shadow:
        0 10px 24px rgba(37, 99, 235, 0.25);
}

.title {
    margin-top: 18px;

    font-size: 30px;
    font-weight: 800;

    color: #0f172a;

    letter-spacing: 0.5px;
}

.subtitle {
    margin-top: 5px;

    font-size: 13px;

    color: #64748b;

    letter-spacing: 0.3px;
}

.spinner {
    width: 32px;
    height: 32px;

    margin-top: 25px;

    border-radius: 50%;

    border:
        3px solid #dbeafe;

    border-top-color: #2563eb;

    animation:
        spin 0.85s linear infinite;
}

.status {
    margin-top: 17px;

    font-size: 14px;

    color: #334155;

    font-weight: 600;
}

.message {
    margin-top: 7px;

    font-size: 12px;

    color: #94a3b8;

    text-align: center;
}

.company {
    margin-top: 20px;

    font-size: 11px;

    color: #94a3b8;

    letter-spacing: 0.4px;
}

@keyframes spin {
    from {
        transform: rotate(0deg);
    }

    to {
        transform: rotate(360deg);
    }
}

</style>
</head>

<body>

<div class="loading-card">

    <div class="logo">
        @@INITIALS@@
    </div>

    <div class="title">
        ${APP_NAME}
    </div>

    <div class="subtitle">
        Complete Business Platform
    </div>

    <div class="spinner"></div>

    <div
        id="status"
        class="status"
    >
        Starting BIZ360...
    </div>

    <div class="message">
        Please wait while the business server starts
    </div>

    <div class="company">
        ${COMPANY_NAME}
    </div>

</div>

<script>

window.setStatus = function(message) {

    const status =
        document.getElementById('status')

    if (status) {
        status.textContent = message
    }

}

</script>

</body>
</html>
`

function updateLoadingStatus(message) {
    if (
        !loadingWindow ||
        loadingWindow.isDestroyed()
    ) {
        return
    }

    try {
        loadingWindow.webContents.executeJavaScript(
            `window.setStatus(${JSON.stringify(message)})`,
            true
        )
    } catch (error) {
        writeElectronLog(
            `Unable to update loading status: ${error.message}`
        )
    }
}

function createLoadingWindow() {

    if (
        loadingWindow &&
        !loadingWindow.isDestroyed()
    ) {
        return
    }

    writeElectronLog(
        'Creating BIZ360 loading window...'
    )

    loadingWindow = new BrowserWindow({

        width: 520,
        height: 390,

        frame: false,

        transparent: true,

        resizable: false,
        movable: false,

        minimizable: false,
        maximizable: false,

        /*
         * User cannot close the splash manually.
         * Electron itself will destroy it.
         */
        closable: false,

        show: false,

        alwaysOnTop: true,

        skipTaskbar: true,

        center: true,

        webPreferences: {

            contextIsolation: true,

            nodeIntegration: false,

            sandbox: true,

            devTools: false
        }
    })

    loadingWindow.loadURL(
        `data:text/html;charset=utf-8,${encodeURIComponent(loadingHtml)}`
    )

    loadingWindow.once(
        'ready-to-show',
        () => {

            if (
                loadingWindow &&
                !loadingWindow.isDestroyed()
            ) {

                loadingWindow.show()

                writeElectronLog(
                    'BIZ360 loading window displayed.'
                )
            }
        }
    )

    loadingWindow.on(
        'closed',
        () => {

            writeElectronLog(
                'BIZ360 loading window closed event received.'
            )

            loadingWindow = null
        }
    )
}

/*
 * IMPORTANT:
 *
 * Do not rely on BrowserWindow.close()
 * for the splash lifecycle.
 *
 * destroy() immediately destroys the
 * BrowserWindow and avoids the splash
 * remaining visible/on-top.
 */
function closeLoadingWindow() {

    if (
        !loadingWindow ||
        loadingWindow.isDestroyed()
    ) {

        loadingWindow = null

        return
    }

    writeElectronLog(
        'Closing BIZ360 loading window...'
    )

    try {

        loadingWindow.setAlwaysOnTop(
            false
        )

        loadingWindow.destroy()

    } catch (error) {

        writeElectronLog(
            `Unable to close loading window: ${error.message}`
        )

        loadingWindow = null
    }
}

/*
 * ============================================================
 * INSTALLATION VALIDATION
 * ============================================================
 */

function validateInstallation() {

    const missing = []

    if (!fs.existsSync(JAVA_EXE)) {
        missing.push(JAVA_EXE)
    }

    if (!fs.existsSync(BACKEND_JAR)) {
        missing.push(BACKEND_JAR)
    }

    /*
     * config\application.properties and config\logback-spring.xml are optional:
     * when missing, the settings packed inside the jar are used.
     */
    if (!fs.existsSync(APPLICATION_PROPERTIES)) {
        writeElectronLog(`Note: ${APPLICATION_PROPERTIES} not found - jar defaults are used.`)
    }

    if (missing.length > 0) {

        const message = [
            'BIZ360 installation is incomplete.',
            '',
            'Missing files:',
            '',
            ...missing,
            '',
            'BIZ360 directory:',
            BIZ360_DIR
        ].join('\n')

        writeElectronLog(message)

        closeLoadingWindow()

        dialog.showErrorBox(
            'BIZ360 Startup Error',
            message
        )

        return false
    }

    return true
}

/*
 * ============================================================
 * PREPARE DIRECTORIES
 * ============================================================
 */

function prepareDirectories() {

    ensureDirectory(LOG_DIR)

    ensureDirectory(BACKUP_DIR)

    writeElectronLog(
        '========================================'
    )

    writeElectronLog(
        `BIZ360 directory: ${BIZ360_DIR}`
    )

    writeElectronLog(
        `Runtime directory: ${RUNTIME_DIR}`
    )

    writeElectronLog(
        `Java executable: ${JAVA_EXE}`
    )

    writeElectronLog(
        `Library directory: ${LIB_DIR}`
    )

    writeElectronLog(
        `Backend JAR: ${BACKEND_JAR}`
    )

    writeElectronLog(
        `Config directory: ${CONFIG_DIR}`
    )

    writeElectronLog(
        `Log directory: ${LOG_DIR}`
    )

    writeElectronLog(
        `Backup directory: ${BACKUP_DIR}`
    )
}

/*
 * ============================================================
 * START SPRING BOOT BACKEND
 * ============================================================
 */

function startBackend() {

    writeElectronLog(
        'Starting BIZ360 backend...'
    )

    const springConfigLocation =
        `file:${CONFIG_DIR}${path.sep}`

    const loggingConfigLocation =
        `file:${LOG_CONFIG}`

    const args = [

        `-DBIZ360_CONFIG_DIR=${CONFIG_DIR}`,

        `-DBIZ360_LOG_DIR=${LOG_DIR}`,

        `-DBIZ360_BACKUP_DIR=${BACKUP_DIR}`,

        '-jar',

        BACKEND_JAR,

        `--server.port=${BACKEND_PORT}`,

        `--spring.config.additional-location=${springConfigLocation}`,

        ...(fs.existsSync(LOG_CONFIG)
            ? [`--logging.config=${loggingConfigLocation}`]
            : [])
    ]

    writeElectronLog(
        `Starting Java: ${JAVA_EXE}`
    )

    writeElectronLog(
        `Java arguments: ${JSON.stringify(args)}`
    )

    backendProcess = spawn(
        JAVA_EXE,
        args,
        {
            cwd: BIZ360_DIR,

            windowsHide: true,

            env: {

                ...process.env,

                JAVA_HOME: JAVA_HOME,

                BIZ360_HOME:
                BIZ360_DIR,

                BIZ360_CONFIG_DIR:
                CONFIG_DIR,

                BIZ360_LOG_DIR:
                LOG_DIR,

                BIZ360_BACKUP_DIR:
                BACKUP_DIR
            },

            stdio: [
                'ignore',
                'pipe',
                'pipe'
            ]
        }
    )

    const backendLogFile =
        path.join(
            LOG_DIR,
            'backend.log'
        )

    const backendErrorLogFile =
        path.join(
            LOG_DIR,
            'backend-error.log'
        )

    backendLogStream =
        fs.createWriteStream(
            backendLogFile,
            {
                flags: 'a'
            }
        )

    backendErrorLogStream =
        fs.createWriteStream(
            backendErrorLogFile,
            {
                flags: 'a'
            }
        )

    backendProcess.stdout.on(
        'data',
        data => {

            const text =
                data.toString()

            backendLogStream.write(text)

            writeElectronLog(
                `BACKEND: ${text.trim()}`
            )
        }
    )

    backendProcess.stderr.on(
        'data',
        data => {

            const text =
                data.toString()

            backendErrorLogStream.write(
                text
            )

            writeElectronLog(
                `BACKEND ERROR: ${text.trim()}`
            )
        }
    )

    backendProcess.on(
        'error',
        error => {

            writeElectronLog(
                `Backend process error: ${error.message}`
            )
        }
    )

    backendProcess.on(
        'exit',
        (code, signal) => {

            writeElectronLog(
                `Backend process exited. Code=${code}, Signal=${signal}`
            )

            backendProcess = null
        }
    )
}

/*
 * ============================================================
 * CHECK BACKEND
 * ============================================================
 */

function checkBackend() {

    return new Promise(resolve => {

        const request =
            http.get(
                BACKEND_URL,
                response => {

                    response.resume()

                    resolve(true)
                }
            )

        request.on(
            'error',
            () => resolve(false)
        )

        request.setTimeout(
            1000,
            () => {

                request.destroy()

                resolve(false)
            }
        )
    })
}

/*
 * ============================================================
 * WAIT FOR SPRING BOOT
 * ============================================================
 */

async function waitForBackend(
    timeoutMs = 180000
) {

    const startTime =
        Date.now()

    updateLoadingStatus(
        'Starting business server...'
    )

    while (
        Date.now() - startTime <
        timeoutMs
        ) {

        if (
            await checkBackend()
        ) {

            writeElectronLog(
                'BIZ360 backend is ready.'
            )

            updateLoadingStatus(
                'Business server started.'
            )

            return true
        }

        await new Promise(
            resolve =>
                setTimeout(
                    resolve,
                    500
                )
        )
    }

    writeElectronLog(
        'BIZ360 backend startup timed out.'
    )

    return false
}

/*
 * ============================================================
 * CREATE MAIN WINDOW
 * ============================================================
 *
 * IMPORTANT CHANGE:
 *
 * Startup completion can happen through:
 *
 * 1. did-finish-load
 * 2. ready-to-show
 * 3. 15 second safety timeout
 *
 * Whichever happens first completes startup.
 *
 * This prevents the splash from being stuck.
 */

function createWindow() {

    return new Promise(resolve => {

        let startupFinished = false

        mainWindow =
            new BrowserWindow({

                width: 1440,

                height: 900,

                minWidth: 1100,

                minHeight: 700,

                show: false,

                autoHideMenuBar: true,

                title: APP_NAME,

                webPreferences: {

                    preload:
                        path.join(
                            __dirname,
                            'preload.cjs'
                        ),

                    contextIsolation: true,

                    nodeIntegration: false,

                    sandbox: true,

                    devTools: false
                }
            })

        /*
         * This function can only run once.
         */
        const finishStartup = () => {

            if (startupFinished) {
                return
            }

            startupFinished = true

            writeElectronLog(
                'BIZ360 renderer startup completed.'
            )

            updateLoadingStatus(
                'BIZ360 is ready!'
            )

            /*
             * Show the actual application first.
             */
            if (
                mainWindow &&
                !mainWindow.isDestroyed()
            ) {

                mainWindow.show()

                mainWindow.focus()

                writeElectronLog(
                    'BIZ360 main window displayed.'
                )
            }

            /*
             * Give Windows/Electron a moment
             * to display the main window before
             * destroying the splash.
             */
            setTimeout(
                () => {

                    closeLoadingWindow()

                    writeElectronLog(
                        'BIZ360 loading window closed.'
                    )

                },
                150
            )

            resolve()
        }

        /*
         * ====================================================
         * FRONTEND FINISHED LOADING
         * ====================================================
         */

        mainWindow.webContents.once(
            'did-finish-load',
            () => {

                writeElectronLog(
                    'BIZ360 frontend finished loading.'
                )

                finishStartup()
            }
        )

        /*
         * ====================================================
         * READY TO SHOW FALLBACK
         * ====================================================
         */

        mainWindow.once(
            'ready-to-show',
            () => {

                writeElectronLog(
                    'BIZ360 main window is ready-to-show.'
                )

                finishStartup()
            }
        )

        /*
         * ====================================================
         * FRONTEND LOAD ERROR
         * ====================================================
         */

        mainWindow.webContents.on(
            'did-fail-load',
            (
                event,
                errorCode,
                errorDescription,
                validatedURL
            ) => {

                writeElectronLog(
                    `Frontend failed to load. ` +
                    `Code=${errorCode}, ` +
                    `Description=${errorDescription}, ` +
                    `URL=${validatedURL}`
                )
            }
        )

        /*
         * ====================================================
         * RENDERER CONSOLE LOG
         * ====================================================
         */

        mainWindow.webContents.on(
            'console-message',
            (
                event,
                level,
                message,
                line,
                sourceId
            ) => {

                writeElectronLog(
                    `[Renderer] ` +
                    `level=${level} ` +
                    `message=${message} ` +
                    `line=${line} ` +
                    `source=${sourceId}`
                )
            }
        )

        /*
         * ====================================================
         * RENDERER CRASH / EXIT
         * ====================================================
         */

        mainWindow.webContents.on(
            'render-process-gone',
            (
                event,
                details
            ) => {

                writeElectronLog(
                    `Renderer process stopped. ` +
                    `reason=${details.reason} ` +
                    `exitCode=${details.exitCode}`
                )
            }
        )

        /*
         * ====================================================
         * MAIN WINDOW CLOSED
         * ====================================================
         */

        mainWindow.on(
            'closed',
            () => {

                writeElectronLog(
                    'BIZ360 main window closed.'
                )

                mainWindow = null
            }
        )

        /*
         * ====================================================
         * LOAD BIZ360 FRONTEND
         * ====================================================
         */

        writeElectronLog(
            `Loading BIZ360 URL: ${BACKEND_URL}`
        )

        mainWindow.loadURL(
            BACKEND_URL
        )

        /*
         * ====================================================
         * SAFETY TIMEOUT
         * ====================================================
         *
         * If neither:
         *
         * did-finish-load
         *
         * nor:
         *
         * ready-to-show
         *
         * happens, do not leave the splash
         * forever.
         */

        setTimeout(
            () => {

                if (!startupFinished) {

                    writeElectronLog(
                        'Frontend startup timeout reached.'
                    )

                    writeElectronLog(
                        'Showing BIZ360 window and closing loading screen.'
                    )

                    finishStartup()
                }

            },
            15000
        )
    })
}

/*
 * ============================================================
 * STOP BACKEND
 * ============================================================
 */

function stopBackend() {

    if (!backendProcess) {
        return
    }

    writeElectronLog(
        'Stopping BIZ360 backend...'
    )

    try {

        backendProcess.kill()

    } catch (error) {

        writeElectronLog(
            `Unable to stop backend: ${error.message}`
        )
    }

    backendProcess = null

    if (backendLogStream) {

        backendLogStream.end()

        backendLogStream = null
    }

    if (backendErrorLogStream) {

        backendErrorLogStream.end()

        backendErrorLogStream = null
    }
}

/*
 * ============================================================
 * SINGLE INSTANCE
 * ============================================================
 */

const gotTheLock =
    app.requestSingleInstanceLock()

if (!gotTheLock) {

    app.quit()

} else {

    app.on(
        'second-instance',
        () => {

            if (mainWindow) {

                if (
                    mainWindow.isMinimized()
                ) {

                    mainWindow.restore()
                }

                if (
                    !mainWindow.isVisible()
                ) {

                    mainWindow.show()
                }

                mainWindow.focus()
            }
        }
    )
}

/*
 * ============================================================
 * APPLICATION READY
 * ============================================================
 */

app.whenReady().then(
    async () => {

        writeElectronLog(
            '========================================'
        )

        writeElectronLog(
            'BIZ360 starting...'
        )

        writeElectronLog(
            `Packaged: ${app.isPackaged}`
        )

        writeElectronLog(
            `Executable: ${process.execPath}`
        )

        writeElectronLog(
            `BIZ360 directory: ${BIZ360_DIR}`
        )

        /*
         * Create splash FIRST.
         */
        createLoadingWindow()

        /*
         * Prepare logs / backup.
         */
        prepareDirectories()

        /*
         * Allow splash to render.
         */
        await new Promise(
            resolve =>
                setTimeout(
                    resolve,
                    150
                )
        )

        /*
         * Validate required portable
         * application files.
         */
        if (
            !validateInstallation()
        ) {

            app.quit()

            return
        }

        updateLoadingStatus(
            'Starting business server...'
        )

        /*
         * Start Spring Boot.
         */
        startBackend()

        /*
         * Wait until Spring Boot responds.
         */
        const backendReady =
            await waitForBackend()

        if (!backendReady) {

            closeLoadingWindow()

            dialog.showErrorBox(
                'BIZ360 Startup Error',
                [
                    'BIZ360 backend could not be started.',
                    '',
                    `BIZ360 directory: ${BIZ360_DIR}`,
                    '',
                    `Java: ${JAVA_EXE}`,
                    '',
                    `Backend: ${BACKEND_JAR}`,
                    '',
                    `Logs: ${LOG_DIR}`
                ].join('\n')
            )

            stopBackend()

            app.quit()

            return
        }

        updateLoadingStatus(
            'Loading BIZ360...'
        )

        /*
         * Create and display main application.
         *
         * createWindow() itself handles:
         *
         * did-finish-load
         * ready-to-show
         * 15 second timeout
         *
         * and closes the splash.
         */
        await createWindow()
    }
)

/*
 * ============================================================
 * MACOS ACTIVATE
 * ============================================================
 */

app.on(
    'activate',
    () => {

        if (
            BrowserWindow.getAllWindows()
                .length === 0
        ) {

            createWindow()
        }
    }
)

/*
 * ============================================================
 * APPLICATION QUIT
 * ============================================================
 */

app.on(
    'before-quit',
    () => {

        writeElectronLog(
            'BIZ360 shutting down...'
        )

        closeLoadingWindow()

        stopBackend()
    }
)

/*
 * ============================================================
 * ALL WINDOWS CLOSED
 * ============================================================
 */

app.on(
    'window-all-closed',
    () => {

        if (
            process.platform !== 'darwin'
        ) {

            app.quit()
        }
    }
)