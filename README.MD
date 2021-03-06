**Profile Manager**  
Сервис предназначается для хранения и управления правами доступа к профилям пользователей.

Для ограничения прав доступа сервисов к информации профилей пользователей используются
маски, по которым фильтруется онтология профиля. В конфигурациях сервиса задается т.н.
онтология типов, которая состоит из всех возможных триплетов, где в качестве субъекта и
объекта выступают типы субъектов и объектов, к которым может быть применен каждый
предикат. Например, триплетам профиля :Michael :studiedAt :ITMOUniversity .
:Michael rdf:type :User . :ITMOUniversity rdf:type :University, будет соответствовать триплет
:User :studiedAt :University. Необходимо, чтобы каждый триплет онтологии типов был достижим
из корневого концепта <http://profmanager.com/User> (так как значение триплета определяется
путем от концепта User до него). На основе онтологи типов формируются маски (подграфы
общей онтологии типов). Сервис получает доступ ко всем триплетам, которые соответствуют
его маске. Например, если маска содержит триплет :User :studiedAt :University, то
сервис получит доступ ко всем университетам, в которых обучался пользователь. Необходимо,
чтобы каждый триплет маски был достижим из концепта User.
В качестве типа любого литерала выступакт концепт rdfs:Literal.

В профиле пользователей (и онтологии типов) могут быть отношения rdfs:subClassOf. В этом
случае можно задать маску таким образом, чтобы фильтрацию проходили только определенные
типы концента. Например, допустим, у концепта :Competence есть свойство :gotAt :EducationUnit,
а у :EducationUnit есть подтипы :University и :School. В онтологии типов
у всех дочерних концептов присутствуют все свойства родительских. И, если задать в маске
триплет с дочерним концептом (:Competence :gotAt :University), то будет произведена
фильтрация по дочернему типу (будут доступны все компетенции, полученные в университете).
Если задать :Competence :gotAt :EducationUnit, то будут возвращены все компетенции.

Также в онтологии типов могут быть заданы не все возможные триплеты профилей, в этом
случае сервису будут доступны эти триплеты, если доступен концепт, заданный в онтологии
типов, которым эти триплеты присоединяются к онтологии типов. Таким образом можно
не разграничивать права доступа к неотъемлимым концептам. Например, имя и адрес
университета :ITMOUniversity :hasName "..." . :ITMOUniversity :hasAddress "..."
можно считать неотъемлимыми от корневого концепта университета :ITMOUniversity. То есть,
если мы знаем, что определенная компетенция была получена в определенном университете, то
мы также можем знать имя, адрес и другую публичную информацию этого университета.

Для работы с сервисом используется REST API (url | parameters | type):
- http://localhost:8080/user | login | POST - создание пользователя с определенным логином
(аутентификация пока не поддерживается и не требуется для выполнения запросов)
- http://localhost:8080/service | sName | POST - создание сервиса с определенным именем.
Сервисам назначаются права доступа к профилям пользователей, в соответствии с которыми
они могут запрашивать информацию
- http://localhost:8080/user/{userLogin}/profile | rem,add,query,sName | POST - запрос для
редактирования онтологии пользователя или ее получения. rem и add содержат описание
удаляемых/добавляемых триплетов в формате TURTLE, закодированном в base64. Могут быть
указаны вместе в одном запросе. query - содержит CONSTRUCT запрос (закодированный в
base64), применяемый к онтологии пользователя для получения определенной информации,
будет выполнен если rem и add не были указаны. sName - имя сервиса, в соответствии с
правами которого запрашивается информация профиля. Если sName не указан - CONSTRUCT
запрос выполняется по всей онтологии профиля, если указан, то вначале профиль
фильтруется в соответсвии с правами сервиса
- http://localhost:8080/user/{userLogin}/profile/rights | sName | GET - запрос
текущих прав сервиса. Возвращается маска (подграф онтологии типов) профиля в 
формате TURTLE, закодированном в base64
- http://localhost:8080/user/{userLogin}/profile/rights | typeOntQuery,isAdd | POST -
добавление/удаление прав сервиса. Предполагается, что изменение прав сервиса таким
образом будет инициироваться пользователем. typeOntQuery - CONSTRUCT запрос по онтологии
типов, результат которого (подграф онтологии типов) будет сложен или вычтен (в
зависимости от параметра isAdd (true|false)) из маски сервиса. Должно выполняться
условие - по завершению операции каждый триплет в маске сервиса должен достигаться
из корневого концепта <http://profmanager.com/User> (не должно быть висячих
в воздухе триплетов)
- http://localhost:8080/user/{userLogin}/profile/rights/request | sName,req,force | POST -
создание запроса на получение прав доступа сервисом. Предполагается, что для получение
прав доступа к профилю сервис создает запрос, который пользователь может подтвержить
или отклонить. req - CONSTRUCT запрос по онтологии типов, закодированный в base64
(результат этого запроса будет добавлен к маске сервиса в случае одобрения
пользователем). В случае, если есть текущий неподтвержденный запрос прав доступа к
профилю этого же пользователя, необходимо указать флаг force=true. Старый запрос будет
закрыт и создан новый.
- http://localhost:8080/user/{userLogin}/profile/rights/request | sName | GET - получить
описание последнего отправленного запроса пользователя в формате JSON.
- http://localhost:8080/user/{userLogin}/profile/rights/request/{reqId}/image | sName | GET -
получить изображение в формате SVG графом маски, заданной в запросе. Красным помечены
новые добавляемые ребра (триплеты). Предполагается, что пользователь делает решение
о предоставлении запрошенных прав после изучения этого графа.
- http://localhost:8080/user/{userLogin}/profile/rights/request/{reqId} | sName,confirm | POST -
подтвердить или отклонить (confirm=true|false) запрос на получение прав доступа сервисом

Предполагается, что какие-то из этих запросов могут выполнять только пользователи, а
какие-то сервисы, но на данный момент аутентификация не реализована.

В файле testCurl.txt приведены примеры curl запросов.

Профили пользователей и другие данные на данный момент хранятся в реляционной базе данных.
По умолчанию используется in-memory база данных, и после перезагрузки сервиса все данные
будут потеряны. Можно задать конфигурационные свойства для использования Postgresql:
- spring.datasource.driver-class-name=org.postgresql.Driver
- spring.datasource.url=jdbc:postgresql://localhost:5432/databasename
- spring.datasource.username=userName
- spring.datasource.password=userPass
- spring.jpa.hibernate.ddl-auto=none

Другие свойства сервиса:
- **server.port** (по умолчанию: 8080) - порт, по которому принимаются REST запросы
- **typeProfileOntologyFilePath** - путь к файлу в формате TURTLE (ttl) с описанием
онтологии типов профиля

Свойства задаются в файле **application.properties**, размещаемом в директории с jar-ником.  
Запустить сервис можно командой:
**java -jar < _jarFileName_ >**

**Текущее состояние**  
На данный момент реализован прототип, в котором не поддерживается аутентификация
пользователей и сервисов, а также не реализован пользовательский интерфейс. Все данные,
включая профили, хранятся в реляционной базе данных и при любом доступе/обновлении
полностью зачитываются из/записываются в базу данных.