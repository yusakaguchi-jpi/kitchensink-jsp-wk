---
title: Using DB2 and Keycloak (Day4)
author: NAGANO Osamu, Red Hat
description: 2024-01-23
paginate: true
headingDivider: 2
---

[GitHubのonagano-rh/kitchensink-jsp-db2](https://github.com/onagano-rh/kitchensink-jsp-db2) について：

- [EAP 7.4のQuickstarts](https://github.com/jboss-developer/jboss-eap-quickstarts/tree/7.4.x) の kitchensink-jsp にS2Iの仕組みでDB2 (AS400版) のドライバを仕込んである
- eap74-sso-s2i のTemplateを使用してKeycloakでアプリを保護
  - このTemplateはPassthrough TLSのRouteを使用しているため [RH-SSOの同様のドキュメント](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.6/html-single/red_hat_single_sign-on_for_openshift/index#deploying_passthrough_tls_termination_templates) が参考になる

ソースの編集も行う者は各自フォークして使うのがよい。その際リポジトリ名は自分のものに読み替えること。



# Keycloakのインストール

認証認可の機能を提供するサーバであるKeycloakをOpenShiftにインストールする。

Keycloak自身が（レルムという単位で）マルチテナントの機能をもっているので、KeycloakはOpenShiftクラスタ内に一つでよい。

Red Hat Build of Keycloak (RHBK) のOperatorを使ってインストールするが、OpenShiftクラスタの管理者権限が必要である。

## RHBK Operatorのインストール

OperatorのインストールはGUIの管理コンソールで行った方が簡単である。

1. OpenShiftの管理コンソールに管理者権限で（ユーザkubeadminもしくはそれ相当）でログインする
2. "Administrator > Operators > OperatorHub" を表示
3. 'Keyclaok Operator (provided by Red Hat)' 検索して "Install" をクリック
4. "Installed Namespace" で新規プロジェクトを作成しそれを選択
   ここではプロジェクト名を "rhbk" とする。
5. "Install" をクリック

これでrhbkプロジェクトにRHBK Operatorがインストールされた。引き続き、同プロジェクト内にこのOperatorで管理されるKeycloakをインストールする。

## RHBKのインストール

KeycloakそのもののインストールはCLIで行うが、引き続き管理者権限を使用する。

```shell
# 管理者権限でログイン
CLUSTER_NAME=<使用する環境に合わせる>
oc login -u kubeadmin https://api.${CLUSTER_NAME}:6443

# RHBK Operatorをインストールしたプロジェクトに移動
oc project rhbk
```

## RHBK用のPostgreSQLをインストール

Keycloakは設定等を保存するためのRDBを一つ必要とする。ここではopenshift名前空間の既存のTemplateを使ってPostgreSQLを作成し利用する。

```shell
oc new-app --template=postgresql-persistent \
  -p POSTGRESQL_VERSION=13-el8 \
  -p DATABASE_SERVICE_NAME=kcpostgres \
  -p POSTGRESQL_USER=kcusername \
  -p POSTGRESQL_PASSWORD=kcpassword \
  -p POSTGRESQL_DATABASE=kcdatabase
```

このとき指定したDATABASE_SERVICE_NAMEと同名のSecretにユーザ名やパスワードが格納されるので、次のKeycloak CR作成時にそれを参照する。

## Keyclaokそのものをインストール

先に作成したDBを指すように設定してKeycloak CR (Custom Resoruce) をインスタンス化する。

```shell
oc apply -f - <<EOF
apiVersion: k8s.keycloak.org/v2alpha1
kind: Keycloak
metadata:
  name: keycloak
spec:
  hostname:
    hostname: keycloak-rhbk.apps.${CLUSTER_NAME}
  instances: 1
  db:
    vendor: postgres
    host: kcpostgres
    usernameSecret:
      key: database-user
      name: kcpostgres
    passwordSecret:
      key: database-password
      name: kcpostgres
    database: kcdatabase
  additionalOptions:
    - name: proxy
      value: edge
EOF
```

## Routeと初期パスワードの確認

Keycloak全体の管理者（masterレルムの管理ユーザ）のユーザ名とパスワードが keycloak-initial-admin というSecretに保存されているのでそれを覗いて確認する。

Secretを確認するにはCLIよりもGUIの管理コンソールの方がよい（Project "rhbk" を選択し "Developer > Secrets > keycloak-inital-admin" を選択し "Reveal values" をクリック、表示されるpasswordをコピーしておく）。

Keycloakの管理コンソールにアクセスするために `oc get route` でホスト名を確認しブラウザでアクセスする。

"Administration Console" をクリックしユーザ名 "admin"、パスワードとしてコピーしたものを使ってログインする。

（masterレルムの）"Users > admin > Credentials > Rest password" をクリックしパスワードを変更しておく（ここでは "password" としておく）。

## テスト用のレルムを作成

masterレルムのユーザadminでの最後の作業として、実際に使用するレルム（ここでは"test-realm"とする）を作成しそのレルム専用の管理ユーザを作成する。

1. 左上のドロップダウンリストから "Create Realm" を選択
2. "Realm name: test-realm" を入力しCreateをクリック
3. "Users > Add user" をクリック
4. "Username: admin" を入力しCreateをクリック
5. "Credentials > Set password" をクリックしパスワードを設定（ここでは "password" とする）
6. "Role mapping > Assign role" をクリックしrealm-adminロールを付与
   "Filter by clients"で"realm-management realm-admin"を探しAssignをクリック

## レルム専用管理コンソールのURLを通達

これでtest-realmの管理者 "admin" がパスワード "password" で作成された。

以下のURLでtest-realmのみの管理が可能な管理コンソールにアクセスできるので、ユーザ名とパスワードと共にtest-realmの管理者に通達し以降は自由に使ってもらう。

- https://keycloak-rhbk.apps.${CLUSTER_NAME}/admin/test-realm/console/

レルム管理者の最初の仕事としては、ユーザを追加することになるであろう。

レルムはマルチテナントやSSOの単位になっており、レルムが異なれば同じユーザ名でも別のユーザとみなされる。



# Keycloakで保護された本プロジェクトのビルドとデプロイ

__WORK IN PROGRESS__

以降の作業はOpenShiftの通常ユーザの権限で行う。

## プロジェクトの作成

```shell

CLUSTER_NAME=<使用する環境に合わせる>
# アカウント名"developer"も自分のものに合わせる
oc login -u developer https://api.${CLUSTER_NAME}:6443

# プロジェクト名には自分のアカウント名を含めるなどして一意性を保つ
oc new-project <適当なプロジェクト名>

source <(oc completion bash)

# 適当な作業用ディレクトリに移動
cd ~/work
```

## EAP公式のImageStreamとTemplateのインポート

```shell
# ImageStreamのインポート
oc apply -f https://raw.githubusercontent.com/jboss-container-images/jboss-eap-openshift-templates/eap74/eap74-openjdk17-image-stream.json

# Templateのインポート
for resource in eap74-amq-persistent-s2i.json eap74-amq-s2i.json eap74-basic-s2i.json eap74-https-s2i.json eap74-sso-s2i.json ; \
  do oc apply -f https://raw.githubusercontent.com/jboss-container-images/jboss-eap-openshift-templates/eap74/templates/${resource}; done

# RH-SSOのImageStreamのインポート
oc import-image sso76-openshift-rhel8 --from=registry.redhat.io/rh-sso-7/sso76-openshift-rhel8 --confirm --request-timeout=5m
```

特に eap74-sso-s2i のTemplateを今回は使用する。
このTemplateがOpenID Connectのライブラリを取得するためのイメージであるRH-SSOのImageStreamも合わせてインポートしている。

## ソースコード取得用のSecretの作成

リポジトリがプライベートであってもアクセスできるように自分のSSH秘密鍵をSecretとして作成しておく。
後で`oc new-app`の際に使用する。

```shell
oc create secret generic my-github-key \
  --from-file=ssh-privatekey=${HOME}/.ssh/id_rsa --type=kubernetes.io/ssh-auth
```

## Passthroug TLS の設定

```shell
# Passthroug TLS setting documentation:
# https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.6/html-single/red_hat_single_sign-on_for_openshift/index#deploying_passthrough_tls_termination_templates

oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default

# Install `openssl` first by `apt install openssl`
# Remember password for "Enter PEM pass phrase: " prompt
openssl req -new -newkey rsa:4096 -x509 -keyout xpaas.key -out xpaas.crt -days 365 -subj "/CN=xpaas-sso-demo.ca"

keytool -genkeypair -keyalg RSA -keysize 2048 -dname "CN=secure-myapp-$(oc project -q).apps.${CLUSTER_NAME}" -alias jboss -keystore keystore.jks -storepass mykeystorepass

keytool -certreq -keyalg rsa -alias jboss -keystore keystore.jks -file sso.csr -storepass mykeystorepass

# Provide the same password as "PEM pass phrase"
openssl x509 -req -extfile  <(printf "subjectAltName=DNS:secure-myapp-$(oc project -q).apps.${CLUSTER_NAME}") -CA xpaas.crt -CAkey xpaas.key -in sso.csr -out sso.crt -days 365 -CAcreateserial

# Type "yes" to the prompt
keytool -import -file xpaas.crt -alias xpaas.ca -keystore keystore.jks -storepass mykeystorepass

keytool -import -file sso.crt -alias jboss -keystore keystore.jks -storepass mykeystorepass

keytool -genseckey -alias secret-key -storetype JCEKS -keystore jgroups.jceks -keyalg Blowfish -keysize 56 -storepass password

# Type "yes" to the prompt
keytool -import -file xpaas.crt -alias xpaas.ca -keystore truststore.jks -storepass mykeystorepass

oc create secret generic eap7-app-secret --from-file=keystore.jks --from-file=jgroups.jceks --from-file=truststore.jks

oc secrets link default eap7-app-secret
```

## データソース作成用のConfigMapの作成

今回使用するTemplateのeap74-sso-s2iでは自動ではextensionsディレクトリをマウントしてくれないため、`oc new-app`直後に自分で`oc set volume`コマンドでマウントすることになる。その際に使用するConfigMapを事前に作成しておく。

```shell
oc create configmap jboss-cli --from-file=postconfigure.sh=extensions/postconfigure.sh --from-file=config-database.cli=extensions/config-database.cli
```

## アプリのビルドとデプロイ

```shell
# コードの変更をいずれ行うつもりなら自分用にForkしてそれを使うこと
MY_GITHUB_REPOSITORY=onagano-rh/kitchensink-jsp-db2

oc new-app --template=eap74-sso-s2i \
  -p APPLICATION_NAME=myapp  \
  -p IMAGE_STREAM_NAMESPACE=$(oc project -q) \
  -p EAP_IMAGE_NAME=jboss-eap74-openjdk17-openshift:latest \
  -p EAP_RUNTIME_IMAGE_NAME=jboss-eap74-openjdk17-runtime-openshift:latest \
  -p SSO_IMAGE_NAME=sso76-openshift-rhel8:latest \
  -p SOURCE_REPOSITORY_URL=git@github.com:${MY_GITHUB_REPOSITORY}.git \
  -p SOURCE_REPOSITORY_REF=main \
  -p CONTEXT_DIR="" \
  --source-secret=my-github-key \
  -p ARTIFACT_DIR=target \
  -p HOSTNAME_HTTPS="secure-myapp-$(oc project -q).apps.${CLUSTER_NAME}" \
  -p HTTPS_SECRET=eap7-app-secret -p HTTPS_KEYSTORE=keystore.jks -p HTTPS_NAME=jboss -p HTTPS_PASSWORD=mykeystorepass \
  -p JGROUPS_ENCRYPT_SECRET=eap7-app-secret -p JGROUPS_ENCRYPT_KEYSTORE=jgroups.jceks -p JGROUPS_ENCRYPT_NAME=secret-key -p JGROUPS_ENCRYPT_PASSWORD=password \
  -p SSO_TRUSTSTORE_SECRET=eap7-app-secret -p SSO_TRUSTSTORE=truststore.jks -p SSO_TRUSTSTORE_PASSWORD=mykeystorepass \
  -p SSO_URL="https://keycloak-rhbk.apps.${CLUSTER_NAME}" \
  -p SSO_SERVICE_URL="https://keycloak-rhbk.apps.${CLUSTER_NAME}" \
  -p SSO_DISABLE_SSL_CERTIFICATE_VALIDATION="true" \
  -p SSO_REALM="test-realm" \
  -p SSO_USERNAME="admin" \
  -p SSO_PASSWORD="password" \
  -p SSO_ENABLE_CORS="true" \
  -e ENABLE_ACCESS_LOG="true" \
  -e DISABLE_EMBEDDED_JMS_BROKER="true" \
  -e SSO_FORCE_LEGACY_SECURITY="false" \
  -e MYDB_USERNAME=XXXX \
  -e MYDB_PASSWORD=XXXX \
  -e MYDB_DATABASE=XXXX \
  -e MYDB_SERVER=XXXX \
  -e MYDB_PORT=not-used-by-db2

# データソース作成用スクリプトをマウント
oc set volume dc/myapp --add --name=jboss-cli -m /opt/eap/extensions -t configmap --configmap-name=jboss-cli --default-mode='0755' --overwrite
```

`MYDB_` で始まる変数の "XXXX" は用意されたDB2の接続情報に合わせる。

## 様々なocコマンド

```shell
# ビルドの様子を確認 (1/2, Mavenビルドの様子)
oc logs -f bc/myapp-build-artifacts

# ビルドの様子を確認 (2/2, ROOT.warを入れ込むDockerビルドの様子)
oc logs -f bc/myapp

# (コードを編集してpush後に)ビルドを再開
oc start-build myapp-build-artifacts --follow --incremental

# oc new-appで作成されたリソースを全て削除
oc delete all -l application=myapp
```

## 動作確認

```shell
# Routeの確認 ("https://"を付けてブラウザでアクセス)
oc get route
```

