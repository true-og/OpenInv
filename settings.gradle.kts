rootProject.name = "OpenInv"

include(":api", ":plugin")

project(":api").projectDir = file("api")

project(":plugin").projectDir = file("plugin")
