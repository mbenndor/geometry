from zipfile import ZipFile

import logging
import sys
import shutil
import os

# OpenCV version to download. Edit this field if you want a different version.
OPENCV_VERSION = "4.1.2"

OPENCV_ANDROID_DL_URL = "https://downloads.sourceforge.net/project/opencvlibrary/{0}/opencv-{0}-android-sdk.zip"
OPENCV_MODULE_DIR = "opencv"
OPENCV_BASE_DIR = "OpenCV-android-sdk"
OPENCV_ANDROID_SRC_DIR = os.path.join("java", "src", "org", "opencv", "android")

# Full URL to the download of the OpenCV archive.
opencv_url = OPENCV_ANDROID_DL_URL.format(OPENCV_VERSION)
# Location of the OpenCV archive after downloading.
opencv_local_file = opencv_url.split("/")[-1]

logging.basicConfig(format="%(asctime)-15s %(name)s %(message)s", level=logging.DEBUG)
logger = logging.getLogger(__name__)

try:
    import requests
except ImportError:
    logger.error("Requests was not found.")
    logger.error("Install on Linux:")
    logger.error("  python3 -m pip install requests")
    logger.error("Install on Windows:")
    logger.error("  py3 -m pip install requests")

    sys.exit(1)

# Only log from requests when warnings are raised.
logging.getLogger("requests").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

def download_opencv_build():
    """
    Downloads the OpenCV Android build archive for the specified version.
    """
    logger.debug("Downloading OpenCV %s build for Android", OPENCV_VERSION)
    
    try:
        with requests.get(opencv_url, stream=True) as r:
            logger.debug("Expected file size: %.2f MiB", int(r.headers["Content-Length"]) / 1048576)
            logger.debug("This might take a while")

            with open(opencv_local_file, "wb") as f:
                shutil.copyfileobj(r.raw, f)
    except IOError:
        logger.error("Failed to download OpenCV", exc_info=True)
        sys.exit(1)
    
    logger.debug("Download finished")

def unpack_archive():
    """
    Unpacks the previously downloaded OpenCV Android build archive.
    """
    logger.debug("Unzipping OpenCV archive")

    with ZipFile(opencv_local_file, "r") as zf:
        # Simple check. Will probably not work in earlier major versions 
        # due to different directory structures.
        if not zf.namelist()[0].startswith(OPENCV_BASE_DIR):
            logger.error("Unknown directory structure, aborting")
            cleanup()

            sys.exit(1)
        
        zf.extractall()

def move_sdk_dir():
    """
    Moves the SDK dir and renames it so it can get imported as an Android module.
    """
    logger.debug("Moving SDK folder into submodule")

    shutil.move(os.path.join(OPENCV_BASE_DIR, "sdk"), OPENCV_MODULE_DIR)

def cleanup():
    """
    Removes all intermediate files that were created except for the OpenCV module
    directory.
    """
    logger.debug("Cleaning up")

    try:
        if os.path.exists(opencv_local_file):
            logger.debug("Deleting OpenCV build archive")
            os.remove(opencv_local_file)
    except IOError:
        logger.error("Couldn't delete OpenCV build archive", exc_info=True)
    
    try:
        if os.path.exists(OPENCV_BASE_DIR):
            logger.debug("Deleting extracted OpenCV directory")
            shutil.rmtree(OPENCV_BASE_DIR)
    except IOError:
        logger.error("Couldn't delete extracted OpenCV directory", exc_info=True)

def apply_patches():
    """
    Applies the source code patches in order to work with AndMeasure.
    """
    # Path to android package.
    and_package_path = os.path.join(OPENCV_MODULE_DIR, OPENCV_ANDROID_SRC_DIR)

    # Path to source file and file where modifications are going to be made.
    jcv_filepath = os.path.join(and_package_path, "JavaCameraView.java")
    jcv_mod_filepath = os.path.join(and_package_path, "JavaCameraView.java~")

    logger.debug("Applying patches")

    try:
        with open(jcv_filepath, "r") as jcv_source:
            with open(jcv_mod_filepath, "w") as jcv_dest:
                for line in jcv_source:
                    if "FOCUS_MODE_CONTINUOUS_VIDEO" in line:
                        # Apply focus mode patch.
                        jcv_dest.write(line.replace("FOCUS_MODE_CONTINUOUS_VIDEO", "FOCUS_MODE_INFINITY"))
                    elif "protected boolean initializeCamera" in line:
                        # Apply camera getter patch.
                        jcv_dest.write("    public Camera getCamera() { return this.mCamera; }" + os.linesep)
                        jcv_dest.write(line)
                    else:
                        jcv_dest.write(line)
        
        logger.debug("Replacing original source file")
        shutil.move(jcv_mod_filepath, jcv_filepath)
    except IOError:
        logger.error("Couldn't apply patches", exc_info=True)

        # Try to clean up.
        cleanup()

        try:
            # Additionally, remove OpenCV module directory.
            logger.debug("Deleting OpenCV module directory")
            shutil.rmtree(OPENCV_MODULE_DIR)
        except IOError:
            logger.error("Couldn't delete OpenCV module directory", exc_info=True)

if __name__ == "__main__":
    # The code documents itself.
    download_opencv_build()
    unpack_archive()
    move_sdk_dir()
    apply_patches()
    cleanup()