# Kuinka tekoäly oppi sytyttämään salamavalon

Vanha Honor 5C päätyi Espoolaiskodin tekniseen tilaan vartioimaan mittareita.
Huoneessa on pimeää. Siellä ovat kylmän ja lämpimän veden mittarit,
lämmityspiirin painemittari ja lämpömittari, mutta valoa ei juuri koskaan.
Jos mittarien lukemat haluttaisiin talteen automaattisesti, puhelimen pitäisi
osata ottaa kuvia yksin, yöllä ja ilman että kukaan käy painamassa mitään.

Ajatus kuulosti helpolta. Käytännössä siitä tuli kuuden viikon mittainen
selvitystyö Androidin kamerarajapinnasta, Huawein omista erikoisuuksista ja
siitä, mitä vanhalla puhelimella voi tehdä ilman ruutua, ilman USB-kaapelia ja
ilman ihmisen apua.

## Puhelimesta palvelin

Alkuasetelma oli arkinen. Kaapista löytyi Honor 5C, vuoden 2016 Android-puhelin,
jossa on 13 megapikselin kamera ja LED-salama. Puhelin kytkettiin laturiin,
yhdistettiin kotiverkkoon ja siihen asennettiin Termux, jonka avulla siitä
tehtiin pieni etähallittava Linux-kone. Puhelimeen sai SSH-yhteyden, joten sitä
pystyttiin ohjaamaan toiselta tietokoneelta kuten mitä tahansa palvelinta.

Tämän vaiheen teki [openclaw-termux](https://github.com/explysm/openclaw-termux)
-järjestelmässä toiminut agentti Tyko. Se tutki useita tapoja ajastaa
valokuvaus Androidilla, valitsi Termux-pohjaisen ratkaisun, kirjoitti
käynnistysskriptit ja rakensi ajastuksen, joka otti kuvan kolmen tunnin välein.
Puhelin heräsi, ajoi tehtävänsä ja pysyi etäyhteyden päässä.

Melkein kaikki toimi heti.

Paitsi että kuvat olivat mustia.

## Miksi salama ei syttynyt

Android-puhelimessa kamera ja LED-salamavalo käyttävät samaa laitteistoa. Kun
kamera avataan kuvan ottamista varten, se ottaa salamavalon hallintaansa. Jos
valo on jo päällä, kamera sammuttaa sen ja ottaa kuvan vasta sitten.
Komentorivityökaluilla tätä ei saa koordinoitua samalla tavalla kuin puhelimen
omassa kamerasovelluksessa.

Siksi puhelin teki kuusi viikkoa työnsä moitteettomasti ja silti turhaan.
Ajastus toimi, SSH toimi, cron toimi, mutta kuvat olivat käytännössä pelkkää
mustaa. Keskimääräinen kirkkaus jäi noin 0,01 prosenttiin.

## Tekoälylle annettiin tehtävä

Maaliskuun 21. päivänä 2026 toiselle tekoälyagentille annettiin yksinkertainen ohje:
puhelimen on opittava ottamaan kuva salamavalon kanssa, eikä työ lopu ennen kuin
se onnistuu.

Oleellista on, ettei ihminen syöttänyt agentille valmiita selityksiä siitä,
miksi aiemmat yritykset olivat epäonnistuneet. Agentti teki kokeet itse,
kirjoitti skriptejä ja Java-ohjelmia, asensi niitä puhelimeen, ajoi testejä,
luki lokit ja virheilmoitukset sekä vertasi kuvien kirkkautta. Kun jokin ei
onnistunut, se joutui itse päättelemään miksi.

Ensimmäisen päivän aikana kokeiltiin kymmentä eri lähestymistapaa.

## Kymmenen yritystä

Ensimmäinen yritys oli suorin mahdollinen: salamavalo päälle, kuva talteen,
salamavalo pois. Kamera sammutti valon ennen kuvaa. Tulos oli musta.

Toisessa yrityksessä tehtiin oma Java-ohjelma, jonka piti käyttää Androidin
kamerarajapintaa suoraan. Android tappoi ohjelman, koska sitä ei ollut
asennettu oikeaksi sovellukseksi.

Kolmannessa yrityksessä Java-ohjelma käynnistettiin toisella tavalla. Sekin
kaatui, koska tarvittavat kirjastot eivät olleet käytettävissä siinä
ympäristössä.

Neljännessä yrityksessä yritettiin muokata suoraan Termuxin sovellusta.
Androidin allekirjoitusmalli esti sen.

Viidennessä yrityksessä oma sovellus yritettiin rakentaa puhelimessa. Puhelimen
rakennustyökalut eivät sopineet yhteen sen oman järjestelmän kanssa.

Kuudennessa yrityksessä huomattiin jotakin tärkeää. Huawein oma kamerasovellus
osasi kyllä sytyttää salaman. Jos sovelluksen avasi ja laukaisinta painoi
keinotekoisesti ADB:n kautta, kuva onnistui. Tämä oli ensimmäinen oikea
läpimurto – mutta se vaati USB-kaapelin.

Seitsemännessä yrityksessä puhelin yhdistettiin ADB:llä itseensä. Ratkaisu oli
kekseliäs ja toimi hetken, mutta Huawei sammutti ADB-palvelun heti, kun
USB-kaapeli irrotettiin.

Kahdeksannessa yrityksessä rakennettiin oma APK tietokoneella, koska puhelin ei
pystynyt rakentamaan sitä itse. Sovellus pyysi Androidia käyttämään tavallista
salamatilaa. Järjestelmä väitti kaiken onnistuneen, mutta kuva jäi pimeäksi.
Huawein kameran laiteajuri ilmoitti salamavalon lauennen, vaikka se ei ollut
lauennut.

Yhdeksännessä yrityksessä vaihdettiin lähestymistapaa. Androidissa LED-valoa voi
käyttää sekä salamavalona että jatkuvana taskulamppuvalona. Huawein ohjelmisto
sivuutti salamamoodin, mutta jatkuva valo toimi. Kun valo pidettiin päällä
jatkuvasti kuvauksen aikana, kuvasta tuli kirkas.

Kymmenes yritys ratkaisi vielä viimeisen esteen. Kun puhelimen näyttö on
sammutettuna, tavallinen kameran esikatselupinta ei toimi, koska se tarvitsee
näytönohjaimen. Ratkaisu oli ohittaa esikatselu kokonaan ja tallentaa kuva
suoraan `ImageReader`-rajapinnan kautta.

Tästä syntyi ensimmäinen toimiva versio: puhelin otti yksin kuvan pimeässä
huoneessa ilman USB-kaapelia ja ilman että kenenkään piti herättää näyttöä.

## Ratkaisu oli pieni sovellus

Lopullinen ohjelma on hyvin pieni, vain muutamien kymmenien kilotavujen APK.
Kun Termux lähettää sille broadcast-viestin, sovellus avaa kameran, sytyttää
LED-valon jatkuvaan tilaan, odottaa hetken ja tallentaa kuvan. Näin puhelin voi
ottaa kuvan täysin itsenäisesti cron-ajastuksen käynnistämänä.

Ilman valoa kuvan kirkkaus oli noin 0,01 prosenttia. Toimivalla ratkaisulla se
nousi ensin tasolle, jolla mittarit ylipäätään näkyivät.

Mutta tarina ei loppunut siihen.

## Työ jatkui onnistumisen jälkeen

Kun ensimmäinen toimiva ratkaisu oli saatu valmiiksi, alkoi seuraava vaihe:
siitä piti tehdä oikeasti käyttökelpoinen.

Ensimmäinen jatko-ongelma oli valotus. Vaikka salama tai oikeammin jatkuvana
pidetty LED-valo jo toimi, todellisessa teknisessä tilassa automaattivalotus
teki kuvista liian tummia. Huone oli iso ja muuten pimeä, joten kameran
algoritmi alivalotti. Agentti lisäsi sovellukseen käsin säädettävän ISO-arvon
ja valotusajan, kokeili useita yhdistelmiä ja päätyi asetukseen ISO 800 ja
100 millisekuntia. Tällä kirkkaus nousi noin 24 prosenttiin. Kuvista tuli
sellaisia, että mittareita pystyi oikeasti lukemaan.

Toinen jatko-ongelma oli käyttövarmuus. Huawein EMUI-järjestelmässä oli
virransäästökomponentteja, jotka tappoivat välillä Termuxin cron-ajon ja
SSH-palvelimen taustalta. Agentti poisti ADB:n kautta käytöstä paketit
`com.huawei.powergenie` ja `com.huawei.android.hwaps`, jotta puhelin ei itse
sabotoisi omaa automaatiotaan.

Kolmas jatko-ongelma oli tarkennus. Kun kuvia alkoi tulla säännöllisesti,
havaittiin, että osa niistä oli selvästi pehmeitä. Syy ei ollut valossa vaan
siinä, että sovellus tallensi ensimmäisen sopivan näköisen ruudun odottamatta,
että automaattitarkennus olisi varmasti lukittunut.

Tähän tehtiin uusi korjauskierros. Sovellukseen lisättiin eksplisiittinen
AF-lukitus ennen tallennusta, mahdollisuus kiinteään käsitarkennukseen sekä
lokitus, joka kertoi jokaisesta ruudusta tarkennustilan, linssin liikkeen ja
etäisyyden. Testien perusteella automaattitarkennus osui usein noin 1,13
diopteriin, mutta juuri kyseisessä asennuksessa kaikkein terävin tulos saatiin
noin 1,3 diopterin käsitarkennuksella.

Kun uusi versio asennettiin puhelimeen ja ajettiin läpi päästä päähän, tulos
oli edelleen kirkas – noin 24,3 prosenttia – mutta nyt myös selvästi terävämpi.
Mittaritaulut, putkitekstit ja muut yksityiskohdat erottuivat paremmin.
Vertailukuvat kopioitiin myös erilliseen [`gauge-reader`](https://github.com/akaihola/gauge-reader)-projektiin, jossa niitä
voidaan käyttää myöhemmin konenäön harjoitteluun.

Projektin onnistumispiste siirtyi samalla. Ensin tavoite oli saada puhelin
välähtämään. Sen jälkeen tavoite oli saada puhelin tuottamaan automaattisesti
kuvia, joista mittareita voi oikeasti lukea.

## Kuka työn teki

Koko työn – tutkimuksen, kokeilut, skriptit, Java-koodin, APK:n,
dokumentaation ja testauksen – teki tekoäly. Tämä koskee myös epäonnistumisten
selityksiä. Niitä ei kirjoitettu agentille valmiiksi, vaan ne perustuivat sen
omiin kokeisiin, virheilokeihin, testiajoihin ja kuvista mitattuihin tuloksiin.

Ihmisen rooli oli käytännöllinen. Hän antoi tavoitteen, vastasi joihinkin
kysymyksiin, kytki välillä USB-kaapelin, hyväksyi tarvittavia oikeuksia
puhelimen ruudulla ja käynnisti SSH-yhteyden uudelleen silloin kun agentti ei
voinut tehdä sitä itse.

Työ jakautui kahteen päävaiheeseen.

Helmikuussa 2026 gogo-koneella toiminut openclaw-termux ja sen agenttipersoona
Tyko rakensivat perustan: tutkimuksen, Termux-ympäristön, etäyhteydet,
ajastuksen ja ensimmäiset skriptit. Näihin kului yhdeksän istuntoa ja yhteensä
788 API-kutsua.

Maaliskuussa 2026 [Pi](https://pi.dev/)-agentti atom-koneella jatkoi työtä.
Se ratkaisi salamaongelman, rakensi lopullisen sovelluksen, viritti valotuksen,
paransi käyttövarmuutta, korjasi tarkennuksen ja viimeisteli dokumentaation.
Näihin kului seitsemän istuntoa ja yhteensä 1 034 API-kutsua.

Yhteensä projektiin käytettiin 16 istuntoa, 1 822 API-kutsua ja noin
174 miljoonaa tokenia. Anthropicin hinnaston mukaan kustannus oli noin
165 dollaria eli suunnilleen 150 euroa.

## Mitä seuraavaksi

Seuraava vaihe on opettaa kone lukemaan mittareita kuvista.

Kuvissa näkyvät ainakin kylmän veden mittari, lämpimän veden mittari,
lämmityspiirin painemittari ja lämpömittari. Ajatus on rakentaa näille useita
kilpailevia konenäköratkaisuja, vertailla niitä keskenään ja säilyttää lopulta
vain toimivin lähestymistapa.

Tavoitteena ei siis ole enää pelkkä kuvaaminen. Tavoitteena on saada talon
vedenkulutuksesta ja lämmitysjärjestelmän tilasta automaattisesti luettavaa
historiatietoa – ja ehkä myöhemmin myös hälytyksiä poikkeamista.

Vanha puhelin ei ollut romua. Se oli laite, jolle piti vain löytää tehtävä.
Ja sen jälkeen piti vielä löytää tekoäly, joka ei luovuttanut silloinkaan,
kun yhdeksän ensimmäistä yritystä epäonnistuivat.

*Teksti on tekoälyn kirjoittama.*
