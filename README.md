# NAND-Measure

Android application implementing two different algorithms for measuring the distance between the device camera and a black circle. This project is built on OpenCV 4.1.2 and there is **no guarantee** that this application will not break if you choose a different version. This repository includes a setup script which downloads the recommended OpenCV build and sets it up so IntelliJ/Android Studio picks up on it. You may change the library version in the script if you wish.

## Setup

Before cloning the source code, make sure you have Python installed and check that the Python `requests` module is available. Running the following command should check both.

```
joogs@owlspace:~$ python3 -c "import requests as r; print(r.__version__);"
2.23.0
```

The setup script has been written for Python 3 and I cannot say for certain that it will work with Python 2.

1. Clone this repository into your workspace.
2. Run `python setup.py`. The setup script will do a couple things.
    * It will download a copy of the OpenCV 4.1.2 build for Android.
    * It will move the necessary contents into the project root directory so OpenCV can be included as a module.
    * It will apply two patches to the `JavaCameraView` in order for NAND-Measure to work.
3. Import the project into IntelliJ/Android Studio.

Make sure your Android SDK is up to date. Gradle sync might fail, but that is usually fixed by restarting your IDE or invalidating caches and then restarting.

## License

MIT, see [LICENSE](LICENSE).