## Sample Authentication Module for Apache Tomcat based on Jakarta Authentication

### Jakarta Authenticationの利用を想定するケース
以下のケースを想定する。
- ゲートウェイ(リバースプロキシ)の役割を果たすHTTPサーバがSAML認証のサービスプロバイダ(SP)となるよう設定済み
- ゲートウェイからバックエンドのTomcatに送信されるリクエストは全てSAML認証済み
- アプリケーションは独自にユーザを管理しており、SAML認証情報のユーザIDとアプリケーションのユーザIDの紐付けが必要
- SAML認証済みのユーザであっても、アプリケーションのユーザではない場合あり
- SAML認証ユーザIDひとつに対し、複数のアプリケーションのユーザIDが紐づく場合があり、ログイン時に利用するユーザIDを選択する
- 認可はアプリケーションのユーザIDをもとに実施したい
- 認可はServlet標準のコンテナによる認可機能を利用したい

### Jakarta Authenticationを利用する理由
Servletコンテナによる認可の実施タイミングは仕様に明記されておらず、Servletフィルタの実行前にコンテナによる認可が実施される可能性があるため、Servletフィルタで認証を実装すると、コンテナが提供する認可を利用することができない。
Jakarta Authenticationによる認証処理は、Servletコンテナによる認可の実施前に実施すること、コンテナによる認可はJakarta Authenticationによる認証結果を利用することが仕様に明記されており、これを採用する。

### 利用した環境
- Java SE: 21
- Jakarta Authentication: 3.0
- Jakarta Servlet: 6.0
- Apache Tomcat: 10.1
- Apache Derby: 10.17

### サンプルでのSAML認証のシミュレーション
SAML認証済みのリクエストは、ゲートウェイサーバで"REMOTE_USER"リクエスト属性にユーザIDが設定されるが、本サンプルは、ゲートウェイサーバを設けず、Apache Tomcatが直接クライアントからのリクエストを受け付けるため、"externalUserId"クエリパラメータでシミュレートする。

### 環境設定

#### Apache Derbyの起動
```shell
# Apache Derby環境変数の定義
export JAVA_HOME=/path/to/java/home
export DERBY_HOME=/path/to/derby/home
export PATH=${DERBY_HOME}/bin:${JAVA_HOME}/bin:${PATH}
# Apache Derbyの実行ディレクトリ(データベースオブジェクト、データ、ログの出力先)
DERBY_BASE=/path/to/derby/base
mkdir ${DERBY_BASE}
# Apache Derbyの起動
cd ${DERBY_BASE}
startNetworkServer
```

#### テスト用のデータベースの作成とデータの登録
```shell
# Apache Derby環境変数の定義
export JAVA_HOME=/path/to/java/home
export DERBY_HOME=/path/to/derby/home
export PATH=${DERBY_HOME}/bin:${JAVA_HOME}/bin:${PATH}
# 対話的sql実行ユーティリティijの起動
DERBY_CLIENT_BASE=/path/to/derby/base
mkdir ${DERBY_CLIENT_BASE}
cd ${DERBY_CLIENT_BASE}
PROJECT_BASE=/path/to/this/maven/project/base
ij
```
```sql
-- テスト用データベースの作成と接続
CONNECT 'jdbc:derby://localhost:1527/appdb;create=true';
-- sqlスクリプトファイルパスに環境変数を用いているが、ij環境内では展開されないため、
-- 実際に実行する際は、展開したファイルパスを指定してください。
-- テスト用テーブルの作成
RUN '${PROJECT_BASE}/sql/create_table_app_user.sql';
-- テスト用データの登録
RUN '${PROJECT_BASE}/sql/insert_test_data_into_app_user.sql';
-- テスト用データベースへの接続解除
DISCONNECT;
-- 対話的sql実行ユーティリティijの終了
EXIT;
```

#### Apache Derbyの停止
```shell
# Apache Derby環境変数の定義
export JAVA_HOME=/path/to/java/home
export DERBY_HOME=/path/to/derby/home
export PATH=${DERBY_HOME}/bin:${JAVA_HOME}/bin:${PATH}
# Apache Derbyの停止
stopNetworkServer
```

#### Apache Tomcatの初期設定
```shell
# Apache Derby環境変数の定義
export DERBY_HOME=/path/to/derby/home
# Apache Tomcat環境変数の定義
export CATALINA_HOME=/path/to/catalina/home
export CATALINA_BASE=/path/to/catalina/base

# CATALINA_BASEのディレクトリ作成
mkdir ${CATALINA_BASE}
mkdir ${CATALINA_BASE}/conf
mkdir ${CATALINA_BASE}/lib
mkdir ${CATALINA_BASE}/logs
mkdir ${CATALINA_BASE}/webapps
mkdir ${CATALINA_BASE}/work
mkdir ${CATALINA_BASE}/temp
# Apache Derby JDBCドライバのコピー
cp -p ${DERBY_HOME}/lib/*.jar ${CATALINA_BASE}/lib/
# デフォルトの設定ファイルコピー
cp -pR ${CATALINA_HOME}/conf/* ${CATALINA_BASE}/conf/
# 設定ファイルの上書き
PROJECT_BASE=/path/to/this/maven/project/base
cp -pR ${PROJECT_BASE}/tomcat-conf/* ${CATALINA_BASE}/conf/
```

#### 認証モジュールとアプリケーションのデプロイ
```shell
export CATALINA_BASE=/path/to/catalina/base
PROJECT_BASE=/path/to/this/maven/project/base
cp -p ${PROJECT_BASE}/auth-module/target/auth-module.jar ${CATALINA_BASE}/lib/
cp -p ${PROJECT_BASE}/app-module/target/app-module.war ${CATALINA_BASE}/webapps/
```

#### Tomcatの起動と停止
```shell
export JAVA_HOME=/path/to/java/home
export CATALINA_HOME=/path/to/catalina/home
export CATALINA_BASE=/path/to/catalina/base
${CATALINA_HOME}/bin/startup.sh
${CATALINA_HOME}/bin/shutdown.sh
```

### 稼働確認

#### アプリケーションリソース
```
# セッションが共有されないよう個別に実施
# SAML認証情報がない場合は403レスポンス
http://localhost:8080/app-module/application.html
# SAML認証済みでも、アプリケーションの登録ユーザでない場合は403レスポンス
http://localhost:8080/app-module/application.html?externalUserId=Dummy
# SAML認証済みで、アプリケーションの登録ユーザの場合は表示
http://localhost:8080/app-module/application.html?externalUserId=Ulysses
```

#### ログインリクエスト
```
# セッションが共有されないよう個別に実施
# SAML認証情報がない場合は403レスポンス
http://localhost:8080/app-module/login.html
# SAML認証済みでも、アプリケーションの登録ユーザでない場合は403レスポンス
http://localhost:8080/app-module/login.html?externalUserId=Dummy
# 適切なSAML認証情報があっても、アプリケーションのユーザIDが指定されていなければ、403レスポンス
http://localhost:8080/app-module/login.html?externalUserId=Ulysses
# 適切なSAML認証情報があり、アプリケーションのユーザIDが指定されていても、未登録であれば403レスポンス
http://localhost:8080/app-module/login.html?externalUserId=Ulysses&appUserId=Dummy
# 適切なSAML認証情報とアプリケーションのユーザIDの指定があれば表示
http://localhost:8080/app-module/login.html?externalUserId=Ulysses&appUserId=Evans
```

#### ログイン後に利用可能なリソースはログイン前はアクセス不可
```
# セッションが共有されないよう個別に実施
# SAML認証情報がない場合は403レスポンス
http://localhost:8080/app-module/forEmployee.html
http://localhost:8080/app-module/forManager.html
http://localhost:8080/app-module/forAdmin.html
# SAML認証情報が存在しても403レスポンス
http://localhost:8080/app-module/forEmployee.html?externalUserId=Emily
http://localhost:8080/app-module/forManager.html?externalUserId=Mike
http://localhost:8080/app-module/forAdmin.html?externalUserId=Alex
# SAML認証情報があり、適切なアプリケーションのユーザIDが指定されていても、ログインを経ていない場合は403レスポンス
http://localhost:8080/app-module/forEmployee.html?externalUserId=Emily&appUserId=Edmonds
http://localhost:8080/app-module/forManager.html?externalUserId=Mike&appUserId=Miles
http://localhost:8080/app-module/forAdmin.html?externalUserId=Alex&appUserId=Adams
```

#### ログイン後に利用可能なリソースへの適切なアクセス

##### Edmonds, Emilyセッション
```
#　以下3つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Emily
http://localhost:8080/app-module/login.html?externalUserId=Emily&appUserId=Edmonds
http://localhost:8080/app-module/forEmployee.html
#　以下2つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forManager.html
http://localhost:8080/app-module/forAdmin.html
```

##### Miles, Mikeセッション
```
#　以下4つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Mike
http://localhost:8080/app-module/login.html?externalUserId=Mike&appUserId=Miles
http://localhost:8080/app-module/forEmployee.html
http://localhost:8080/app-module/forManager.html
#　以下1つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forAdmin.html
```

##### Adams, Alexセッション
```
#　以下3つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Alex
http://localhost:8080/app-module/login.html?externalUserId=Alex&appUserId=Adams
http://localhost:8080/app-module/forAdmin.html
#　以下2つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forEmployee.html
http://localhost:8080/app-module/forManager.html
```

##### Evans, Ulyssesセッション
```
#　以下3つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Ulysses
http://localhost:8080/app-module/login.html?externalUserId=Ulysses&appUserId=Evans
http://localhost:8080/app-module/forEmployee.html
#　以下2つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forManager.html
http://localhost:8080/app-module/forAdmin.html
```

##### Morris, Ulyssesセッション
```
#　以下4つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Ulysses
http://localhost:8080/app-module/login.html?externalUserId=Ulysses&appUserId=Morris
http://localhost:8080/app-module/forEmployee.html
http://localhost:8080/app-module/forManager.html
#　以下1つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forAdmin.html
```

##### Allen, Ulyssesセッション
```
#　以下3つのリソースは正常に表示される
http://localhost:8080/app-module/application.html?externalUserId=Ulysses
http://localhost:8080/app-module/login.html?externalUserId=Ulysses&appUserId=Allen
http://localhost:8080/app-module/forAdmin.html
#　以下2つのリソースは権限がないため、403レスポンス
http://localhost:8080/app-module/forEmployee.html
http://localhost:8080/app-module/forManager.html
```
