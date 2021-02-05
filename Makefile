ARDUINO_PATH=/opt/arduino
CLASSPATH:=$(ARDUINO_PATH)/lib/arduino-core.jar:$(ARDUINO_PATH)/lib/pde.jar
CLASSPATH:=$(CLASSPATH):$(wildcard $(ARDUINO_PATH)/lib/rsyntaxtextarea*.jar)
# :$(ARDUINO_PATH)/lib/rsyntaxtextarea-3.0.3-SNAPSHOT.jar
TGTOPT=--release 8
JAVAVER=$(shell javac -version 2>&1)
PROJ=IniIno
PRPATH=com.google.tykefcz.iniino

ifneq (,$(findstring 1.8,$(JAVAVER)))
  TGTOPT=-target 1.8
else 
  ifneq (,$(findstring 1.7,$(JAVAVER)))
    TGTOPT=-target 1.8
  else 
    ifneq (,$(findstring 1.9,$(JAVAVER)))
      TGTOPT=-target 1.8
    endif
  endif
endif

all:
	test -d bin || mkdir bin
	javac $(TGTOPT) -Xlint:deprecation -Xlint:unchecked -cp $(CLASSPATH) -d bin ./src/*.java
	cd bin ; jar -cfe $(PROJ).jar $(PRPATH).$(PROJ) $(subst .,/,$(PRPATH))/*.class
	
debug:
	javac -verbose -Xlint:all -cp $(CLASSPATH) -d bin ./src/*.java

clean:
	rm -rf bin
	rm -f *~ src/*~

zipup:
	cd .. ; zip -r -y -x $(PROJ)/bin/\* -FS ~/Nextcloud/ZipSync/$(PROJ).zip $(PROJ)

zipdn:
	cd .. ; unzip -u ~/Nextcloud/ZipSync/$(PROJ).zip
