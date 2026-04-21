include(
    ":retrograde-util",
    ":retrograde-app-shared",
    ":lemuroid-touchinput",
    ":lemuroid-app",
    ":lemuroid-metadata-libretro-db",
    ":lemuroid-app-ext-free",
    ":bundled-cores",
    ":libretrodroid",
    ":radialgamepad"
)

project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")
