cmake_minimum_required(VERSION 3.4.1)

# Compiler options
add_compile_options(-Wall -DA3D_GLESv2)

# Main library
add_library(loaxserver
            SHARED

            # Source
            android_jni.c)

# Submodules
add_subdirectory("a3d")
add_subdirectory("net")
add_subdirectory("loax")

# Linking
target_link_libraries(loaxserver

                      # Submodule libraries
                      ${LIBS_JPEG}
                      a3d
                      net
                      loax

                      # NDK libraries
                      android
                      log)
