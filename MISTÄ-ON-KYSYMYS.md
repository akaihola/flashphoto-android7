# Kuinka tekoäly oppi sytyttämään salamavalon

*Kuuden viikon tarina vanhasta puhelimesta, pimeästä huoneesta ja tekoälystä,
joka ei suostunut luovuttamaan.*

---

## Lähtökohta

Espoolaiskodin teknisessä tilassa – siinä huoneessa, jossa vesimittarit,
kaukolämpömittari ja lämmityspiirin painemittarit sijaitsevat – on pimeää.
Täysin pimeää. Valoa ei ole, koska kukaan ei käy siellä päivittäin.

Mutta entä jos mittareiden lukemat haluttaisiin tallentaa automaattisesti?
Entä jos vanha, käyttämätön puhelin voisi ottaa valokuvia mittareista muutaman
tunnin välein – salamavalon kanssa – ja lähettää ne eteenpäin analysoitaviksi?

Idea kuulostaa yksinkertaiselta. Se ei ollut sitä.

---

## Osa 1: Vanha puhelin saa uuden tehtävän

Kaapista löytyi Honor 5C – vuoden 2016 Android-puhelin, jossa on 13 megapikselin
kamera ja LED-salama. Puhelin liitettiin laturiin, yhdistettiin kodin
Wi-Fi-verkkoon ja siihen asennettiin Termux – sovellus, joka muuttaa
Android-puhelimen pieneksi Linux-tietokoneeksi. Termuxin avulla puhelimeen saa
SSH-etäyhteyden: sitä voi ohjata toiselta tietokoneelta samalla tavalla kuin
mitä tahansa palvelinta.

Tämän alkutyön teki [openclaw-termux](https://github.com/explysm/openclaw-termux)
-järjestelmässä toiminut tekoälyagentti nimeltä Tyko, joka toimi toisella
kotitietokoneella ja keskusteli ihmisen kanssa Telegramin kautta. Agentti tutki
viisi eri tapaa ottaa valokuvia Androidilla ajastettuna, vertaili niiden etuja
ja haittoja, ja suositteli komentorivillä toimivaa Termux-ratkaisua. Se
kirjoitti käynnistysskriptit, ajasti valokuvauksen kolmen tunnin välein ja
huolehti, että SSH-yhteys ja ajastin käynnistyvät automaattisesti uudelleen,
jos puhelin sattuisi käynnistymään uudelleen.

Kaikki näytti hyvältä – paitsi yksi asia. Kuvat olivat täysin mustia.

---

## Osa 2: Miksi salama ei syttynyt

Android-puhelimen kamera ja LED-salamavalo jakavat saman laitteiston. Kun
kamerasovellus avaa kameran ottaakseen kuvan, se ottaa salamavalon yksinoikeudella
haltuunsa. Jos salamavalo on päällä valokuvaa otettaessa, kamera sammuttaa sen
ensin – ja ottaa sitten kuvan pimeässä.

Tämä on suunnittelupäätös, ei virhe. Android-käyttöjärjestelmä toimii näin
tarkoituksella, koska kamera ja salamavalo eivät voi toimia samanaikaisesti
ilman erityistä koordinaatiota. Normaali kamerasovellus hoitaa tämän
koordinaation sisäisesti: se sytyttää salamavalon juuri oikealla hetkellä
valotuksen aikana. Mutta komentorivillä toimiva Termux-työkalu ei tätä osaa.

Kuusi viikkoa puhelin otti tunnollisesti kuvan joka kolmas tunti. Kuusi viikkoa
kaikki kuvat olivat käytännössä mustia – keskimääräinen kirkkaus 0,01 prosenttia.
Hädin tuskin mitään.

---

## Osa 3: Tekoäly saa tehtävän

21\. maaliskuuta 2026 ihminen antoi toiselle tekoälyagentille – Claude-nimiselle
kielimallille – tehtävän:

> *"Tavoitteesi on saada puhelin ottamaan valokuva salamavalon kanssa. Kokeile,
> tutki, testaa, toista. Älä lopeta ennen kuin olet onnistunut."*

Alkoi intensiivinen ongelmanratkaisupäivä. Ihminen ei syöttänyt tekoälylle
valmiita selostuksia epäonnistumisista, vaan agentti teki kokeet itse:
kirjoitti skriptejä ja ohjelmia, asensi niitä puhelimeen, ajoi testejä,
luki virheilmoituksia ja lokitulosteita sekä päätteli niiden perusteella,
miksi kukin yritys epäonnistui. Tekoäly kokeili kymmentä eri
lähestymistapaa – joista yhdeksän epäonnistui.

---

## Osa 4: Kymmenen yritystä

**Yritys 1: Salamavalo päälle, kuva, salamavalo pois.** Kamera sammutti
salamavalon. Musta kuva.

**Yritys 2: Oma Java-ohjelma, joka käskee kameraa käyttämään salamaa.** Android
tappoi ohjelman välittömästi, koska se ei ollut virallisesti asennettu
sovellus.

**Yritys 3: Java-ohjelma toisella käynnistystavalla.** Epäonnistui
toisella tavalla – tarvittavat kirjastot eivät olleet käytettävissä.

**Yritys 4: Termux-sovelluksen muokkaaminen suoraan.** Ei onnistunut, koska
Androidin tietoturva vaatii, että sovellukset on allekirjoitettu samalla
avaimella, eikä alkuperäistä avainta ollut käytettävissä.

**Yritys 5: Oman sovelluksen rakentaminen puhelimessa.** Puhelimen
kehitystyökalut eivät olleet yhteensopivia puhelimen oman käyttöjärjestelmän
kanssa.

**Yritys 6: Kamerasovelluksen käyttöliittymän simulointi.** Tekoäly huomasi, että
Huawein oma kamerasovellus osaa sytyttää salaman. Entä jos kamerasovellus
käynnistettäisiin ohjelmallisesti ja "painettaisiin" laukaisinnappia
simuloimalla kosketusta näytöllä? Se toimi! Kuva oli kirkas, salama syttyi.

Mutta tämä vaati USB-kaapelin. Kosketuksen simulointi edellytti niin sanottua
ADB-yhteyttä, joka toimii vain USB-kaapelin kautta. Ja Honor-puhelimen
Huawei-käyttöjärjestelmä katkaisee ADB-yhteyden välittömästi, kun USB-kaapeli
irrotetaan – toisin kuin tavallinen Android.

**Yritys 7: Puhelin yhdistää ADB:n itseensä.** Tekoäly keksi, että puhelin voi
asentaa oman ADB-työkalun ja yhdistää sillä itseensä – puhelin ohjaa itseään.
Nerokas ratkaisu, joka toimi. Mutta vaati silti USB-kaapelin, koska Huawei
sammuttaa ADB-palvelun kaapelin irrotessa.

**Yritys 8: Oma sovellus, rakennettu tietokoneella.** Koska puhelimessa
rakentaminen ei onnistunut, tekoäly rakensi sovelluksen tietokoneella
käyttäen viittä eri työkalua ja siirsi valmiin sovelluksen puhelimeen.
Sovellus käytti Androidin virallista kamerarajapintaa ja pyysi kameraa
sytyttämään salaman.

Kamera vastasi: "Salama syttyi." Mutta kuva oli pimeä. Puhelimen
laitteisto-ohjain – Huawein oma ohjelmistokerros, joka ohjaa fyysistä
kameralaitteistoa – *valehteli*. Se ilmoitti salamavalon syttyneen, vaikka
ei ollut oikeasti sytyttänyt sitä.

**Yritys 9: Taskulamppuvalo kamerasession aikana.** Androidissa on kaksi tapaa
käyttää LED-valoa: salamavalona (yksi välähdys kuvan aikana) ja taskulamppuna
(jatkuva valo). Huawein ohjelmisto ohitti salamamoodin, mutta
taskulamppumoodi toimi – koska se käyttää eri koodireittiä laitteiston
ohjauksessa. Tekoäly pyysi kameraa pitämään LED-valon päällä jatkuvasti kuvan
ottamisen ajan. **Kuva oli kirkas.**

**Yritys 10: Toiminta ilman näyttöä.** Viimeinen este. Kun puhelimen näyttö on
pimeänä (kuten se on 99,9 % ajasta valvontakäytössä), kameran
esikatselukuva vaatii näytönohjaimen – joka on sammuksissa. Sovellus kaatui.
Ratkaisu: ohittaa esikatselukuva kokonaan ja ohjata kameran data suoraan
kuvatiedostoon.

---

## Osa 5: Ratkaisu

Lopullinen sovellus on pieni – noin 16 kilotavua, pienempi kuin tämä teksti.
Se tekee yhden asian: kun Termux lähettää sille viestin, se avaa kameran,
sytyttää LED-valon taskulamppumoodissa, odottaa kaksi sekuntia valon ja
kameran tasapainottumista, ottaa kuvan ja tallentaa sen.

Puhelimeen ei tarvita USB-kaapelia, näyttöä ei tarvitse herättää, kenenkään ei
tarvitse koskea puhelimeen. Kolmen tunnin välein puhelimen oma ajastin
käynnistää sovelluksen, ja puhelin ottaa kuvan.

Tulokset ovat selkeitä. Ilman salamaa kuvan keskimääräinen kirkkaus on 0,01 % –
käytännössä musta. Salamavaloratkaisulla kirkkaus on noin 24 % – riittävä
mittareiden lukemiseen.

---

## Osa 6: Kuka teki työn?

Koko projektin – tutkimuksen, kokeilut, koodin, skriptit, dokumentoinnin ja
tämän projektikansion – teki tekoäly. Tämä tarkoittaa myös sitä, että
kertomuksen epäonnistumisten syyt eivät tulleet ihmiseltä valmiina syötteinä,
vaan perustuivat agentin omiin kokeisiin, testiajoihin, virheilmoituksiin,
lokitulosteisiin ja kuvista mitattuihin tuloksiin. Ihmisen rooli oli antaa
tehtävä, vastata muutamaan kysymykseen, kytkeä ja irrottaa USB-kaapeli
pyydettäessä ja käynnistää SSH-yhteys uudelleen silloin, kun tekoäly ei siihen
itse kyennyt.

Työ jakautui kahteen vaiheeseen kahdella eri tekoälyjärjestelmällä:

**Helmikuu 2026:** gogo-koneella Termux-ympäristössä toiminut
openclaw-termux-järjestelmä ja sen agenttipersoona Tyko, joka käytti
Claude Opus 4.5:tä – tutkimus, puhelimen käyttöönotto, ensimmäiset
skriptit, dokumentointi. Yhdeksän istuntoa, yhteensä 788 API-kutsua.

**Maaliskuu 2026:** Pi-agentti atom-koneella, pääosin Claude Opus 4.6:lla
ja osin Claude Sonnet 4.6:lla – salama-ongelman ratkaisu, kymmenen
epäonnistunutta kokeilua, lopullinen APK-sovellus. Neljä istuntoa,
yhteensä 607 API-kutsua.

Yhteensä 13 istuntoa, 1 395 API-kutsua ja noin 131 miljoonaa tokenia. Kustannus
Anthropicin API-hinnaston mukaan: noin 110 dollaria eli reilut 100 euroa.

---

## Osa 7: Mitä seuraavaksi?

Nyt kun puhelin osaa ottaa valaistuja kuvia pimeässä huoneessa, seuraava askel
on opettaa tekoälyä lukemaan mittareita kuvista:

- **Kylmän veden mittari** – kuutiometrejä, juoksevat numerot
- **Lämpimän veden mittari** – samoin
- **Patterilämmityksen painemittari** – bareja, analoginen viisari
- **Patterilämmityksen lämpötilamittari** – celsiusasteita, analoginen viisari

Tavoitteena on seurata vedenkulutusta ja lämmitysjärjestelmän tilaa automaattisesti sekä hälyttää
poikkeavuuksista – esimerkiksi äkillisestä vedenkulutuksen kasvusta (vuoto?) tai
lämmityspaineen laskusta (ilmaa järjestelmässä?).

Vanha puhelin laatikossa ei ollut arvoton. Se vain tarvitsi tehtävän – ja
tekoälyn, joka ei luovuttanut yhdeksän epäonnistumisen jälkeen.

---

*Tämä teksti on tekoälyn kirjoittama.*
