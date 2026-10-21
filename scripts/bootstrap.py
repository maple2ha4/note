"""Install checksum-verified Maven and configure the existing environment proxy."""
import hashlib
import html
import os
from pathlib import Path
import tarfile
import urllib.parse
import urllib.request

root = Path(os.environ.get('NOTES_TOOLS_DIR', '/workspace/tools'))
root.mkdir(parents=True, exist_ok=True)
version = '3.9.11'
maven = root / f'apache-maven-{version}'
if not (maven / 'bin/mvn').is_file():
    url = f'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/{version}/apache-maven-{version}-bin.tar.gz'
    data = urllib.request.urlopen(url, timeout=60).read()
    checksum = urllib.request.urlopen(url + '.sha512', timeout=30).read().decode().split()[0]
    if hashlib.sha512(data).hexdigest() != checksum:
        raise SystemExit('Maven checksum verification failed')
    archive = root / 'maven.tar.gz'
    archive.write_bytes(data)
    with tarfile.open(archive) as bundle:
        bundle.extractall(root, filter='data')
    archive.unlink()
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
settings = '<settings>'
if proxy.hostname:
    settings += '<proxies><proxy><id>environment</id><active>true</active><protocol>http</protocol>'
    settings += '<host>' + html.escape(proxy.hostname) + '</host><port>' + str(proxy.port or 80) + '</port>'
    if proxy.username:
        settings += '<username>' + html.escape(urllib.parse.unquote(proxy.username)) + '</username>'
    if proxy.password:
        settings += '<password>' + html.escape(urllib.parse.unquote(proxy.password)) + '</password>'
    settings += '</proxy></proxies>'
settings += '</settings>'
config = root / 'maven-settings.xml'
config.write_text(settings)
config.chmod(0o600)
print('Maven is prepared. TLS and checksum verification remain enabled.')
