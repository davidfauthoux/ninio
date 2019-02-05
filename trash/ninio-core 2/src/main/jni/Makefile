UNAME  := $(shell uname)
CYGWIN := $(findstring CYGWIN,$(UNAME))
DARWIN := $(findstring Darwin,$(UNAME))

CC       = gcc
SHARED   = -shared
CFLAGS   = -Wall -O2 -pipe -D_REENTRANT
WINSOCK  = ws2_32
EXTRA_LDFLAGS  =
EXTRA_CPPFLAGS =
LDFLAGS  = $(EXTRA_LDFLAGS)
CPPFLAGS = $(EXTRA_CPPFLAGS)
LIBNAME  = libNativeRawSocket
LIBEXTENSION = so

ifeq ($(DARWIN),Darwin)
  JAVA_INCDIR  = $(JAVA_HOME)/include
  JAVA_INCDIR_PLAF = $(dir $(wildcard $(JAVA_INCDIR)/*/jni_md.h))
  CPPFLAGS += -I$(JAVA_INCDIR) -I$(JAVA_INCDIR_PLAF)
  LIBEXTENSION = dylib
  LDFLAGS += -dynamiclib -single_module -framework JavaVM
  SHARED =
else
  ifeq ($(CYGWIN),CYGWIN)
    override CC += -mno-cygwin
    CPPFLAGS += -D__int64="long long"
    LDFLAGS += -Wl,--kill-at -l$(WINSOCK)
    JDK_HOME := $(shell cygpath $(JDK_HOME))
    LIBNAME = NativeRawSocket
    LIBEXTENSION = dll
  endif

  JAVA_INCDIR      = $(JDK_HOME)/include
  JAVA_INCDIR_PLAF = $(dir $(wildcard $(JAVA_INCDIR)/*/jni_md.h))
  CPPFLAGS += -I$(JAVA_INCDIR) -I$(JAVA_INCDIR_PLAF)
  CFLAGS += -ansi -pthread -fPIC -DPIC
endif

SRC := $(shell find . -name "*.c" -print)
OBJ := $(SRC:%.c=%.o)

CLEAN_EXTENSIONS = o $(LIBEXTENSION)

LIB = $(LIBNAME).$(LIBEXTENSION)

all: $(LIB)

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -c $< -o $@

$(LIB): $(OBJ)
	$(CC) $(SHARED) -o $@ $^ $(LDFLAGS)

clean:
	for extension in $(CLEAN_EXTENSIONS); do \
		find . -name "*.$$extension" | xargs rm -f ; \
	done
	find . -name "*~" | xargs rm -f
