#
#
#

SELENIUM_DATA_PATH_FILE := data/SeleniumDataPathLocation
SELENIUM_DATA_PATH      := $(shell cat $(SELENIUM_DATA_PATH_FILE))
CHROME_FOR_TESTING_DIR  := $(SELENIUM_DATA_PATH)/chrome-for-testing

CHROME_FOR_TESTING_VERSION_FILE := data/ChromeForTestingVersion
CHROME_FOR_TESTING_VERSION      := $(shell cat $(CHROME_FOR_TESTING_VERSION_FILE))

all: check-selenium-data-path
	@echo "CHROME_FOR_TESTING_VERSION  $(CHROME_FOR_TESTING_VERSION)"
	@echo "CHROME_FOR_TESTING_DIR      $(CHROME_FOR_TESTING_DIR)"

build:
	mvn ant:ant install

full-build:
	mvn clean ant:ant install


check-selenium-data-path:
	@echo "SELENIUM_DATA_PATH_FILE  !$(SELENIUM_DATA_PATH_FILE)!"
	@echo "SELENIUM_DATA_PATH       !$(SELENIUM_DATA_PATH)!"
	@if [ ! -d $(SELENIUM_DATA_PATH) ]; then \
		echo "no directory  !${SELENIUM_DATA_PATH}!" ; \
		exit 1 ; \
	fi

check-chrome-for-testing:
	@echo "CHROME_FOR_TESTING_VERSION  $(CHROME_FOR_TESTING_VERSION)"
	@echo "CHROME_FOR_TESTING_DIR      $(CHROME_FOR_TESTING_DIR)"
	@if [ ! -d $(CHROME_FOR_TESTING_DIR) ]; then \
		echo "no directory  !${CHROME_FOR_TESTING_DIR}!" ; \
		exit 1 ; \
	fi


download-chrome-for-testing: check-selenium-data-path
	echo "chrome for testing version $(CHROME_FOR_TESTING_VERSION)"; \
	mkdir -p $(CHROME_FOR_TESTING_DIR); \
	curl -s --output $(CHROME_FOR_TESTING_DIR)/chrome-version https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_$(CHROME_FOR_TESTING_VERSION); \
	chrome_version=`/bin/cat $(CHROME_FOR_TESTING_DIR)/chrome-version`; \
	echo "chrome for testing version $${chrome_version}"; \
	mkdir -p $(CHROME_FOR_TESTING_DIR)/$${chrome_version}; \
	rm -f $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chrome-mac-arm64.zip; \
	rm -f $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chromedriver-mac-arm64.zip; \
	curl --output $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chrome-mac-arm64.zip       https://storage.googleapis.com/chrome-for-testing-public/$${chrome_version}/mac-arm64/chrome-mac-arm64.zip; \
	curl --output $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chromedriver-mac-arm64.zip https://storage.googleapis.com/chrome-for-testing-public/$${chrome_version}/mac-arm64/chromedriver-mac-arm64.zip; \
	ls -lha $(CHROME_FOR_TESTING_DIR)/$${chrome_version}


setup-chrome-for-testing: check-selenium-data-path
	echo "chrome for testing version $(CHROME_FOR_TESTING_VERSION)"; \
	mkdir -p $(CHROME_FOR_TESTING_DIR); \
	chrome_version=`/bin/cat $(CHROME_FOR_TESTING_DIR)/chrome-version`; \
	echo "chrome for testing version $${chrome_version}"; \
	rm -rf $(CHROME_FOR_TESTING_DIR)/chrome-mac-arm64; \
	rm -rf $(CHROME_FOR_TESTING_DIR)/chromedriver-mac-arm64;  \
	unzip -q -d $(CHROME_FOR_TESTING_DIR) $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chrome-mac-arm64.zip ; \
	unzip -q -d $(CHROME_FOR_TESTING_DIR) $(CHROME_FOR_TESTING_DIR)/$${chrome_version}/chromedriver-mac-arm64.zip; \
	ls -lha $(CHROME_FOR_TESTING_DIR)


open-chrome-for-testing: check-chrome-for-testing
	open -n '$(CHROME_FOR_TESTING_DIR)/chrome-mac-arm64/Google Chrome for Testing.app' --args --user-data-dir=$(CHROME_FOR_TESTING_DIR)/user-data-dir


