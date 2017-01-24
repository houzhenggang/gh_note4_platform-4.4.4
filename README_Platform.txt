How to build Module for Platform
- It is only for modules are needed to using Android build system.
- Please check its own install information under its folder for other module.

[Step to build]
1. Get android open source.
    : version info - Android 4.4
    ( Download site : http://source.android.com )

2. Copy module that you want to build - to original android open source
   If same module exist in android open source, you should replace it. (no overwrite)
   
  # It is possible to build all modules at once.
  
3. You should add module name to 'PRODUCT_PACKAGES' in 'build\target\product\core.mk'.

ex.) [build\target\product\core.mk]

	# ProfessionalAudio
	PRODUCT_PACKAGES += \
	    libjackshm \
	    libjackserver \
	    libjack \
	    androidshmservice \
	    jackd \
	    jack_dummy \
	    jack_alsa \
	    jack_goldfish \
	    jack_opensles \
	    jack_loopback \
	    in \
	    out \
	    jack_connect \
	    jack_disconnect \
	    jack_lsp \
	    jack_showtime \
	    jack_simple_client \
	    jack_transport \
	    libasound \
	    libglib-2.0 \
	    libgthread-2.0 \
	    libfluidsynth
    
	# strongSwan
	PRODUCT_PACKAGES += \
	    charon \
	    libcharon \
	    libhydra \
	    libstrongswan
    
	# e2fsprog
	PRODUCT_PACKAGES += \
	    e2fsck \
	    resize2fs
    
	# libexifa
	PRODUCT_PACKAGES += \
	    libexifa
    
	# libjpega
	PRODUCT_PACKAGES += \
	    libjpega
    
	# KeyUtils
	PRODUCT_PACKAGES += \
	    libkeyutils
    
	# junit, webkit
	PRODUCT_PACKAGES += \
	    junit \
	    libwebcore
	    
4. excute build command
   ./build.sh user

5. How to clean
	make clean

Note : 
to build SBrowser (vendor/samsung/packages/apps/SBrowser),
please refer to Buildme.txt at the folder mentioned above.   

to build mhi.ko (vendor/qcom/opensource/mhi),
please refer to How to build mhi.ko.txt at the folder mentioned above.
