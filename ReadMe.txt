Plik ReadMe do programu NetworkNode

Spis treści:
1. Uruchamianie programu (węzła)
2. Przyłączanie nowego węzła i komunikacja między węzłami (TCP + UDP lub UDP + UDP)
3. Komunikacja z klientem (TCP)
4. Wykorzystane wewnętrzne polecenia do komunikacji między węzłami oraz ich opis
5. Kompilacja z poziomu wiersza poleceń
6. Implementacja
7. Polecenia debugujące
8. Dodatkowe



            _____1. Uruchamianie programu (węzła)_____
Program można uruchomić z poziomu wiersza poleceń przy pomocy komendy:

java NetworkNode

Obsługiwane są następujące parametry przy uruchamianiu:
-ident   <id>         -> Jest to identyfikator danego węzła, powinen być liczbą naturalną, zalecana jest unikatowość, lecz nie jest obowiązkowa;
-udpport <port>       -> Ustawia port na którym program nasłuchuje oraz wymusza PIERWSZĄ komunikację z nowym węzłem przy pomocy UDP, pozostałe zapytania wewnętrzne przy pomocy UDP;
-tcpport <port>       -> Ustawia port na którym program nasłuchuje oraz wymusza PIERWSZĄ komunikację z nowym węzłem przy pomocy TCP, pozostałe zapytania wewnętrzne przy pomocy UDP;
-gateway <ip:port>    -> Ustawia ip oraz port "Rodzica", z którym nowo powstały węzeł komunikuje się w celu podłączenia do reszty;
"zasoby" <zasob:ilos> -> Ustawia zasób oraz jego ilość, przyjmowane są typy zasobów A - Z w dowolnej ilości, aż do MAX_INT;

Program do komunikacji z klientem zawsze posługuje się protokołem TCP.

Przykłowa postać uruchomienia węzła sieci:

java NetworkNode -ident <identyfikator> -tcpport(-udpport) <numer portu TCP> -gateway <adres>:<port> <lista zasobów>

Przykładowe wywołanie:

java NetworkNode -ident 123 -tcpport 9991 -gateway localhost:9990 A:5 C:3

PIERWSZY węzeł powinen być uruchamiany bez -gateway, gdyż nie ma on swojego "Rodzica".


            _____2. Przyłączanie nowego węzła i komunikacja między węzłami (TCP + UDP lub UDP + UDP)_____
Po uruchomieniu pierwszego węzła na np. porcie 9000 można uruchomić kolejny w celu dołączenia go do już istniejącego węzła.
Uruchamiamy drugi węzeł podając w parametrze -gateway IP:PORT pierwszego węzła, w przypadku jednego komputera: 127.0.0.1:9000 (ew. localhost:9000).
W parametrze -tcpport (ew. -udpport) musimy podać inny port, niż aktualnie używany. Nie można przypisać tego samego portu więcej niż raz.
W momencie uruchamiania drugiego i/lub kolejnego węzła wsyła on polecenie "NEW_NODE" do istniejącego już węzła ("Rodzica").
"Rodzic" odbiera polecenie "NEW_NODE" i nastęopuje wymiana danych od "Dziecka" do "Rodzica". Po udanej wymianie rodzic wysyła polecenie "ACCEPTED".
"Dziecko" odbiera polecenie "ACCEPTED", które informuje je, że zostało poprawnie dodane do sieci oraz otrzymuje dokładne dane swojego "Rodzica" (zasoby).
Po pojawieniu się kolejnych węzłów, węzły informują pokolei swoich "Rodziców" o pojawieniu się nowego węzła za pomocą polecenia "NEW_LIST".
Po otrzymaniu informacji węzeł stojący najwyżej rozsyła po każdym dołączeniu nowego węzła zaktualizowaną listę węzłów aktualnie podłączonych do siebie (wszystkie istniejące węzły).
Dzieje się to przy pomocy polecenia "UPDATED_LIST".
Po otrzymaniu "UPDATED_LIST" węzły są świadome innych węzłów oraz są gotowe do pracy.


            _____3. Komunikacja z klientem (TCP)_____
Klient wysyła prośbę o alokację zasobów do wybranego przez sobie węzła (jednego w danym momencie) w postaci:

<identyfikator> <zasób>:<liczność> [<zasób>:liczność]

Następuje wtedy proces sprawdzenia przez wybrany węzeł, który węzeł posiada zasoby żądane przez klienta oraz czy cała sieć jest wstanie spełnić żądanie klienta.
Jeżeli jeden węzeł nie jest wstanie spełnić żądania całkowicie, zasoby dobierane są z kolejnych węzłów (o ile te węzły posiadają wymagany zasób).
Po zweryfikowaniu oraz zarezerwowaniu "jaki zasób i w którym węzle", klient otrzymuje wiadomość(ew. wiadomości), gdzie ma zgłosić się (w którym węźle) znajdują się poszczególne zasoby oraz w jakiej ilości.

<zasób>:<liczność>:<ip węzła>:<port węzła>

Serwer następie każde zwolnić każdemu z węzłów zarezerwowane zasoby poleceniem "GET_ITEM", a następie aktualny stan zasobów po operacji jest aktualizowany w całej ścieci za pomocą polecenia "REQUEST".
W przypadku braku wymaganej liczby zasobów w sieci, klient otrzymuje odpowiedź "FAILED".
Następnie połączenie z klientem zostaje zakończone.

W przypadku otrzymania od klienta polecenia "TERMINATE", cała sieć jest wyłączana (zamknęcie procesów).


            _____4. Wykorzystane wewnętrzne polecenia do komunikacji między węzłami oraz ich opis_____
NEW_NODE     -> Informuje "Rodzica" o powstaniu nowego węzła, przekazuje informacje o nowo powstałym "Dziecku";
ACCEPTED     -> Informuje "Dziecko" o dodaniu go do istniejącej sieci, przekazuje szczegółowe informacje o "Rodzicu";
NEW_LIST     -> Informuje "Rodzica" o powstaniu nowego węzła (węzłów) pod jego "Dzieckiem";
UPDATED_LIST -> Aktualizuje posiadaną przez węzły listę aktualnie istniejących węzłów w sieci;
GET_ITEM     -> Nakazuje zwolnić wcześniej zarezerwowane zasoby;
REQUEST      -> Aktualizuje aktualny stan zasobów danego węzła, jest wysyłane pozostałym węzłom w sieci;


            _____5. Kompilacja z poziomu wiersza poleceń_____
Należy otworzyć konsolę w lokalizacji gdzie znajduje się plik: "NetworkNode.java".
Następnie należy wpisać w konsoli: 'javac NetworkNode.java' i wcisnąć enter.
Następnie aby uruchomić program należy wpisać w konsoli: 'java NetworkNode'.
Dokładny proces uruchamiania został opisany w punkcie 1.


            _____6. Implementacja_____
Zaimplementowane zostały:
    1) Tworzenie i podłączanie kolejnych węzłów do sieci
    2) Komunikacja z klientem, przyjmowanie żądań, alokacja zasobów wewnątrz węzła
    3) Alokacja zasobów w całej sieci (w kilku węzłach)


            _____7. Polecenia debugujące_____
Polecenia dodatkowe wywoływane w konsoli podczas działania programu (w celu debugowania):
'close' -> zamyka Socket
'showNodes' -> pokazuje aktualną liste "Kontaktów" węzła na którym zostanie użyta komenda


            _____8. Dodatkowe_____
Program przeszedł testy na próbnych, dostarczonych skryptach.
Nie została wychwycona żadna nieprawidłowość podczas testów.


