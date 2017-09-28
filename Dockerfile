FROM java:8-jdk
MAINTAINER Shisei Hanai<shanai@jp.ibm.com>

RUN apt-get update
RUN apt-get install wget tesseract-ocr imagemagick curl poppler-utils -y

ADD date /usr/share/tesseract-ocr/tessdata/configs/
ADD ENdate /usr/share/tesseract-ocr/tessdata/configs/
ADD signedDigits /usr/share/tesseract-ocr/tessdata/configs/
RUN useradd -d "/var/home" -s /bin/bash formuser
RUN mkdir -p /opt/formbuilder
ADD target/universal /opt/formbuilder

RUN cd /opt/formbuilder && \
  wget http://www.bouncycastle.org/download/bcprov-jdk15on-155.jar && \
  cp bcprov-jdk15on-155.jar $JAVA_HOME/jre/lib/ext/ && \
  sed -e \
  "/^security.provider.9=sun.security.smartcardio.SunPCSC$/i security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider" \
  $JAVA_HOME/jre/lib/security/java.security

RUN cd /opt/formbuilder && \
  cmd=$(basename *.tgz .tgz) && \
  tar xf $cmd.tgz

RUN cd /opt/formbuilder && \
  cmd=$(basename *.tgz .tgz) && \
  echo "#!/bin/bash -xe" > launch.sh && \
  echo printenv >> launch.sh && \
  echo "ls -lh /opt/formbuilder" >> launch.sh && \
  echo /opt/formbuilder/$cmd/bin/formBuilderServer -J-Xmx2048m -DmoduleName=$cmd -Dplay.crypto.secret=\${APP_SECRET} >> launch.sh && \
  chmod +x launch.sh

RUN chown -R formuser:formuser /opt/formbuilder
USER formuser

EXPOSE 9000

ENTRYPOINT ["/bin/bash", "-c", "/opt/formbuilder/launch.sh"]