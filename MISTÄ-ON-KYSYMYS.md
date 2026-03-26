# Kuinka tekoäly opetti vanhan puhelimen kuvaamaan pimeässä

**Espoolaiskodin teknisessä tilassa vanhan Honor-puhelimen pitäisi lukea
maailmaa silloin, kun kukaan ei katso. Ensin sen piti vain oppia sytyttämään
salamavalo.**

Vanhalla Honor 5C -puhelimella on uusi tehtävä Espoolaiskodin teknisessä
tilassa. Sen pitäisi ottaa kuvia kylmän ja lämpimän veden mittareista,
lämmityspiirin painemittarista ja lämpömittarista täysin itsenäisesti,
yölläkin ja ilman että kukaan käy paikan päällä painamassa mitään.

Huoneessa on pimeää, ja ilman salamavaloa kuvat ovat käytännössä mustia.
Ajatus tuntui silti aluksi suoraviivaiselta: vanha puhelin laturiin,
langattomaan verkkoon ja ottamaan kuvia kolmen tunnin välein. Käytännössä siitä
tuli kuuden viikon selvitystyö Androidin kamerarajapinnasta, Huawein omista
poikkeuksista ja siitä, mitä lukitulla puhelimella voi tehdä ilman USB-kaapelia
ja ilman ihmisen jatkuvaa apua.

## Kaapista löytyi puhelin, puhelimesta tuli palvelin

Lähtöasetelma oli arkinen. Kaapista löytyi vuoden 2016 Honor 5C, jossa on
13 megapikselin kamera ja LED-salama. Puhelin kytkettiin laturiin,
yhdistettiin kotiverkkoon ja siihen asennettiin Termux. Sen avulla puhelimesta
tehtiin pieni etähallittava Linux-kone. Puhelimeen sai SSH-yhteyden, joten sitä
pystyttiin ohjaamaan toiselta tietokoneelta kuten mitä tahansa palvelinta.

Perustyön teki [openclaw-termux](https://github.com/explysm/openclaw-termux)
-järjestelmässä toiminut agentti Tyko. Se tutki useita tapoja ajastaa kuvaus
Androidilla, valitsi Termux-pohjaisen ratkaisun, kirjoitti käynnistysskriptit
ja rakensi ajastuksen, joka otti kuvan kolmen tunnin välein.

Melkein kaikki toimi heti.

Paitsi että kuvat olivat mustia.

## Android sammutti valon itse

Puhelimen kamera ja LED-salamavalo käyttävät samaa laitteistoa. Kun kamera
avataan, se ottaa valon hallintaansa. Jos valo on jo päällä, kamera sammuttaa
sen ja ottaa kuvan vasta sitten. Puhelimen oma kamerasovellus osaa ajoittaa
välähdyksen oikein, mutta komentorivityökalu ei.

Siksi puhelin teki kuusi viikkoa työnsä moitteettomasti ja silti turhaan.
Ajastus toimi, SSH toimi, cron toimi, mutta kuvat olivat lähes pelkkää mustaa.
Keskimääräinen kirkkaus jäi noin 0,01 prosenttiin.

## Sitten tekoäly sai yksinkertaisen käskyn

Maaliskuun 21. päivänä 2026 toiselle tekoälyagentille annettiin tehtävä: puhelin
pitää saada ottamaan kuva salamavalon kanssa, eikä työ lopu ennen kuin se
onnistuu.

Oleellista on, ettei ihminen syöttänyt agentille valmiita selityksiä siitä,
miksi aiemmat yritykset olivat epäonnistuneet. Agentti teki kokeet itse.
Se kirjoitti skriptejä ja Java-ohjelmia, asensi niitä puhelimeen, ajoi testejä,
luki lokit ja virheilmoitukset sekä vertasi kuvien kirkkautta. Kun jokin ei
onnistunut, sen piti itse päätellä miksi.

Ensimmäisen päivän aikana kokeiltiin kymmentä eri lähestymistapaa.

## Yhdeksän hutia ja yksi osuma

Ensimmäinen yritys oli suorin mahdollinen: salamavalo päälle, kuva talteen,
salamavalo pois. Kamera sammutti valon ennen kuvaa. Tulos oli musta.

Sitten tehtiin oma Java-ohjelma, joka käyttäisi Androidin kamerarajapintaa
suoraan. Android tappoi ohjelman. Käynnistystapaa vaihdettiin. Sekin kaatui.
Yritettiin muokata Termuxin sovellusta. Allekirjoitusmalli esti sen. Yritettiin
rakentaa oma sovellus puhelimessa. Rakennustyökalut eivät toimineet yhteen
puhelimen järjestelmän kanssa.

Ensimmäinen oikea läpimurto tuli vasta, kun huomattiin jotakin tärkeää:
Huawein oma kamerasovellus kyllä osasi sytyttää salaman. Jos sovellus avattiin
ja laukaisinta painettiin ADB:n kautta, kuva onnistui. Menetelmä vain vaati
USB-kaapelin, eikä sitä ollut tarkoitus jättää pysyvästi kiinni.

Seuraavaksi puhelin yhdistettiin ADB:llä itseensä. Ratkaisu oli kekseliäs ja
hetken aikaa toimiva, mutta Huawei sammutti ADB-palvelun heti, kun USB-kaapeli
irrotettiin.

Kun oma APK viimein rakennettiin tietokoneella ja asennettiin puhelimeen,
Androidin tavallinen salamatila näytti ensin lupaavalta. Järjestelmä väitti,
että salamavalo laukesi. Kuva jäi silti pimeäksi. Huawein kameran laiteajuri
ilmoitti välähdyksen tapahtuneen, vaikka valoa ei todellisuudessa tullut.

Ratkaisu löytyi vasta kiertotietä. Androidissa samaa LED-valoa voi käyttää
sekä salamavalona että jatkuvana taskulamppuvalona. Huawein ohjelmisto sivuutti
salamamoodin, mutta jatkuva valo toimi. Kun valo pidettiin päällä kuvauksen
ajan, kuva kirkastui.

Vielä yksi este piti ohittaa. Kun puhelimen näyttö on sammutettuna,
tavallinen esikatselupinta ei toimi, koska se tarvitsee näytönohjaimen.
Ratkaisuksi tuli esikatselun ohittaminen kokonaan ja kuvan tallentaminen
suoraan kameran puskurista.

Silloin syntyi ensimmäinen versio, joka todella toimi: puhelin otti yksin kuvan
pimeässä huoneessa ilman USB-kaapelia ja ilman että kenenkään piti herättää
näyttöä.

## Toimiva ratkaisu oli lopulta pieni

Lopullinen ohjelma on pieni APK. Kun Termux lähettää sille broadcast-viestin,
sovellus avaa kameran, sytyttää LED-valon jatkuvaan tilaan, odottaa hetken ja
tallentaa kuvan. Näin puhelin voi ottaa kuvan täysin itsenäisesti cron-ajon
käynnistämänä.

Ilman valoa kuvan kirkkaus oli noin 0,01 prosenttia. Toimivalla ratkaisulla se
nousi ensin tasolle, jolla mittarit ylipäätään näkyivät.

Mutta siinä vaiheessa selvisi, että ensimmäinen onnistuminen ei vielä riittänyt.

## Kun salama toimi, alkoi seuraava työ

Ensimmäinen jatko-ongelma oli valotus. Vaikka valo jo toimi, todellisessa
teknisessä tilassa automaattivalotus teki kuvista liian tummia. Huone oli iso,
muuten pimeä ja kameran algoritmi alivalotti sitkeästi. Agentti lisäsi
sovellukseen käsin säädettävän ISO-arvon ja valotusajan, kokeili useita
asetuksia ja päätyi yhdistelmään ISO 800 ja 100 millisekuntia.

Sillä kirkkaus nousi noin 24 prosenttiin. Kuvista tuli sellaisia, että
mittareita pystyi oikeasti lukemaan.

Toinen jatko-ongelma oli käyttövarmuus. Huawein EMUI-järjestelmässä oli
virransäästökomponentteja, jotka tappoivat välillä Termuxin cron-ajon ja
SSH-palvelimen taustalta. Agentti poisti ADB:n kautta käytöstä paketit
`com.huawei.powergenie` ja `com.huawei.android.hwaps`, jotta puhelin ei itse
sabotoisi automaatiotaan.

Kolmas jatko-ongelma oli tarkennus. Kun kuvia alkoi tulla säännöllisesti,
havaittiin, että osa niistä oli pehmeitä. Syy ei ollut valossa vaan siinä,
että sovellus tallensi ensimmäisen sopivan näköisen ruudun odottamatta, että
automaattitarkennus olisi varmasti lukittunut.

Siksi tehtiin vielä uusi korjauskierros. Sovellukseen lisättiin varma
automaattitarkennuksen lukitus ennen tallennusta, mahdollisuus kiinteään
käsitarkennukseen sekä lokitus, joka kertoi ruuduista tarkennustilan, linssin
liikkeen ja etäisyyden. Testien perusteella automaattitarkennus osui usein
noin 1,13 diopteriin, mutta juuri tässä asennuksessa kaikkein terävin tulos
saatiin noin 1,3 diopterin käsitarkennuksella.

Kun uusi versio asennettiin puhelimeen ja ajettiin läpi päästä päähän, tulos
oli edelleen kirkas – noin 24,3 prosenttia – mutta nyt myös selvästi terävämpi.
Mittaritaulut, putkitekstit ja muut yksityiskohdat erottuivat paremmin.
Vertailukuvat kopioitiin myös erilliseen
[`gauge-reader`](https://github.com/akaihola/gauge-reader)-projektiin, jossa
niitä voidaan käyttää myöhemmin konenäön harjoitteluun.

Samalla projektin onnistumispiste siirtyi. Ensin tavoite oli saada puhelin
välähtämään. Sen jälkeen tavoite oli saada puhelin tuottamaan automaattisesti
kuvia, joista mittareita voi oikeasti lukea.

## Tekoäly teki työn, ihminen auttoi käytännössä

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
