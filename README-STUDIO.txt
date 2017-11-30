How to build with Android Studio
================================

Updater needs access to the system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. To do this:

 - Generate a keystore and keystore.properties using gen-keystore.sh
 - Build the dependencies running 'make UpdaterStudio'. This command will add
   the needed libraries in system_libraries/.

You need to do the above once, unless Android Studio can't find some symbol.
In this case, rebuild the system libraries with 'make UpdaterStudio'.
