

[GitHubのonagano-rh/kitchensink-jsp-db2](https://github.com/onagano-rh/kitchensink-jsp-db2) をフォークして、コピーして作成：

- [EAP 7.4のQuickstarts](https://github.com/jboss-developer/jboss-eap-quickstarts/tree/7.4.x) の kitchensink-jsp にS2Iの仕組みでDB2 (AS400版) のドライバを仕込んである
- eap74-sso-s2i のTemplateを使用してKeycloakでアプリを保護
  - このTemplateはPassthrough TLSのRouteを使用しているため [RH-SSOの同様のドキュメント](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.6/html-single/red_hat_single_sign-on_for_openshift/index#deploying_passthrough_tls_termination_templates) が参考になる

ソースの編集も行う者は各自フォークして使うのがよい。その際リポジトリ名は自分のものに読み替えること。

\\wsl.localhost\Ubuntu\home\jpi20004\work\kitchensink-jsp-wk\src\main\java\org\jboss\as\quickstarts\kitchensinkjsp\mycode
のJAVAの中身のSQLを修正  


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



MY_GITHUB_REPOSITORY=yuksakaguchi-jpi/kitchensink-jsp-wk

oc new-app --template=eap74-basic-s2i \
  -p APPLICATION_NAME=myapp  \
  -p IMAGE_STREAM_NAMESPACE=$(oc project -q) \
  -p EAP_IMAGE_NAME=jboss-eap74-openjdk17-openshift:latest \
  -p EAP_RUNTIME_IMAGE_NAME=jboss-eap74-openjdk17-runtime-openshift:latest \
  -p SOURCE_REPOSITORY_URL=git@github.com:${MY_GITHUB_REPOSITORY}.git \
  -p SOURCE_REPOSITORY_REF=main \
  -p CONTEXT_DIR="" \
  --source-secret=my-github-key \
  -e MYDB_USERNAME=UNWUPF1 \
  -e MYDB_PASSWORD=UNWUPF1 \
  -e MYDB_DATABASE=JPIDTA_KR1 \
  -e MYDB_SERVER=192.1.1.118

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

