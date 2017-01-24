Follow the following stemp to make libsbrowser.so

	1. Download SBrowser OSLV code
	2. run the following command to make libsbrowser.so
		In <SBrowser_Path>
		./src/build_libsbrowser.sh highend
	3. libsbrowser.so. will be located in the following location
		<SBrowser_Path>/src/out/Release/lib/libsbrowser.so

	You have to set PATH for JDK before build.
	And, if you set CCACHE, you can build faster.
	export USE_CCACHE=1
	export CCACHE_EXE=/usr/bin/ccache
