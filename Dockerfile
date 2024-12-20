FROM openjdk:17-jdk

RUN apt-get update && apt-get install -y \
    wget \
    curl \
    unzip \
    firefox-esr \
    && rm -rf /var/lib/apt/lists/*

RUN GECKODRIVER_VERSION=$(curl -s https://api.github.com/repos/mozilla/geckodriver/releases/latest | grep "tag_name" | cut -d '"' -f 4) && \
    wget -q "https://github.com/mozilla/geckodriver/releases/download/$GECKODRIVER_VERSION/geckodriver-$GECKODRIVER_VERSION-linux64.tar.gz" -O /tmp/geckodriver.tar.gz && \
    tar -xzf /tmp/geckodriver.tar.gz -C /usr/local/bin && \
    chmod +x /usr/local/bin/geckodriver && \
    rm -f /tmp/geckodriver.tar.gz

ENV DISPLAY=:99
WORKDIR /app
COPY build/libs/*.jar /


CMD ["java", "-jar", "/app/thocc-project-backend-all.jar"]