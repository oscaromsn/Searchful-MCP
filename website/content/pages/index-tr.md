Title: {{ PAGE_TITLE_OVERVIEW }}
Slug: index
Lang: tr
url:
save_as: index.html
Keywords: masaüstü arama, dosya yönetimi, arama, belge, alma, indeksleme
Description: hızlı belge alımı için bir masaüstü arama uygulaması olan Docfetcher'ın Ana Sayfası

<div class="note" markdown="1">
**Not**: DocFetcher Pro, DocFetcher'ın daha fazla özellik içeren ticari büyük kardeşi veya DocFetcher Server, çok kullanıcılı destek ve web arayüzü ile DocFetcher'ın ticari kuzeni ile ilgilenebilirsiniz. [Daha fazla bilgi](https://docfetcherpro.com/).
</div>

Açıklama
===========
DocFetcher bir Açık Kaynak masaüstü arama uygulamasıdır: Bilgisayarınızdaki dosyaların içeriğini aramanıza izin verir. &mdash; Yerel dosyalarınız için Google olarak düşünebilirsiniz. Uygulama Windows, Linux ve macOS üzerinde çalışır ve [Eclipse Public License](https://en.wikipedia.org/wiki/Eclipse_Public_License) altında kullanıma sunulur.

Temel Kullanım
===========
Aşağıdaki ekran görüntüsü ana kullanıcı arayüzünü göstermektedir. Sorgular (1) 'deki metin alanına girilir. Arama sonuçları (2) 'deki sonuç bölmesinde görüntülenir. (3) 'deki önizleme bölmesi, sonuç bölmesinde o anda seçili olan dosyanın salt metin önizlemesini gösterir. Dosyadaki tüm eşleşmeler sarı ile vurgulanır.

Sonuçları minimum ve/veya maksimum dosya boyutuna (4), dosya türüne (5) ve konuma (6) göre filtreleyebilirsiniz. (7) 'deki düğmeler sırasıyla kılavuzu açmak, tercihleri açmak ve programı sistem tepsisine küçültmek için kullanılır.

<div class="img-container" style="text-align: center;"><a href="/theme/intro-001-results-edited.png"><img src="/theme/intro-001-results-edited.png"></a></div>

DocFetcher, arama yapmak istediğiniz klasörler için sözde *dizinler* oluşturmanızı gerektirir. Dizin oluşturmanın ne olduğu ve nasıl çalıştığı aşağıda daha ayrıntılı olarak açıklanmıştır. Özetle, bir dizin DocFetcher'ın hangi dosyaların belirli bir kelime grubunu içerdiğini çok hızlı bir şekilde (milisaniye sırasına göre) bulmasını ve böylece aramaları büyük ölçüde hızlandırmasını sağlar. Aşağıdaki ekran görüntüsü DocFetcher'ın yeni dizinler oluşturmak için iletişim kutusunu göstermektedir:

<div class="img-container" style="text-align: center;"><a href="/theme/intro-002-config.png"><img src="/theme/intro-002-config.png"></a></div>

Bu iletişim kutusunun sağ alt köşesindeki "Çalıştır" düğmesine tıklama indekslemeyi başlatır. İndeksleme işlemi, indekslenecek dosyaların sayısına ve boyutlarına bağlı olarak biraz zaman alabilir. İyi bir pratik kural, dakikada 200 dosyadır.

Bir dizin oluşturmak zaman alırken, klasör başına yalnızca bir kez yapılmalıdır. Ayrıca, klasör içeriği değiştikten sonra bir dizini *güncellemek*, onu oluşturmaktan çok daha hızlıdır &mdash; genellikle sadece birkaç saniye sürer.

Önemli Özellikler
================
* **Taşınabilir sürümler**: DocFetcher'ın sırasıyla Windows, Linux ve macOS üzerinde çalışan taşınabilir sürümleri vardır. Bu taşınabilir sürümler, *taşınabilir bir belge deposu* oluşturmanıza olanak tanır: Tüm önemli belgelerinizin özgürce hareket ettirebileceğiniz, tamamen indekslenmiş ve tamamen aranabilir bir deposu. Bu, onu bir USB sürücüsünde yanınızda taşıyabileceğiniz, arşivleme amacıyla paketleyebileceğiniz, şifreli bir birime koyabileceğiniz, bir bulut sürücüsü aracılığıyla birden çok bilgisayar arasında senkronize edebileceğiniz ve hatta yükleyip dünyanın geri kalanıyla paylaşabileceğiniz anlamına gelir.
* **Unicode desteği**: DocFetcher, Microsoft Office, OpenOffice.org, PDF, HTML, RTF ve düz metin dosyaları dahil tüm ana formatlar için sağlam Unicode desteği ile birlikte gelir.
* **Arşiv desteği**: DocFetcher şu arşiv biçimlerini destekler: zip, 7z, rar ve tüm tar.* ailesi. Zip arşivleri için dosya uzantıları özelleştirilebilir ve gerektiğinde daha fazla zip tabanlı arşiv biçimi eklemenize olanak tanır. Ayrıca, DocFetcher sınırsız sayıda arşivleri yerleştirebilir (örn., bir rar arşivi içeren 7z arşivi içeren bir zip arşivi... vb.).
* **Kaynak kodu dosyalarında arama**: DocFetcher'ın düz metin dosyalarını tanıdığı dosya uzantıları özelleştirilebilir, böylece DocFetcher'ı her türlü kaynak kodu ve diğer metin tabanlı dosya formatlarında arama yapmak için kullanabilirsiniz. (Bu, özelleştirilebilir zip uzantılarıyla birlikte oldukça iyi çalışır, örn., Jar dosyalarının içindeki Java kaynak kodunda arama yapmak için.)
* **Outlook PST dosyaları**: DocFetcher, Microsoft Outlook'un genellikle PST dosyalarında depoladığı Outlook e-postalarının aranmasına izin verir.
* **HTML çiftlerinin tespiti**: Varsayılan olarak DocFetcher, HTML dosyası çiftlerini (örn., "foo.html" adlı bir dosya ve "foo_files" adlı bir klasör) algılar ve çifti tek bir belge olarak ele alır. Bu özellik ilk bakışta oldukça yararsız görünebilir, ancak HTML klasörlerindeki tüm "karmaşa" sonuçlardan kaybolduğu için, HTML dosyalarıyla uğraşırken arama sonuçlarının kalitesini önemli ölçüde artırdığı ortaya çıktı.
* **Düzenli ifade tabanlı dosyaların indeksleme dışında bırakılması**: Belirli dosyaları indekslemeden çıkarmak için düzenli ifadeleri kullanabilirsiniz. Örneğin, Microsoft Excel dosyalarını tarama dışında bırakmak için şuna benzer bir düzenli ifade kullanabilirsiniz: `.*\.xls`
* **Mime türü algılama**: Belirli dosyalar için "mime türü algılamayı" açmak için normal ifadeleri kullanabilirsiniz; bu, DocFetcher'ın yalnızca dosya adına bakarak değil, aynı zamanda dosya içeriğine göz atarak da gerçek dosya türlerini algılamaya çalışacağı anlamına gelir. Bu, yanlış dosya uzantısına sahip dosyalar için kullanışlıdır.
* **Güçlü sorgu sözdizimi**: DocFetcher, "VEYA", "VE" ve "DEĞİL" gibi temel yapılara ek olarak, diğer şeylerin yanı sıra şunları da destekler: Joker karakterler, kelime öbeği araması, bulanık arama ("... ile benzer kelimeleri bul"), yakınlık araması (" Bu iki kelime birbirinden en fazla 10 kelime uzakta olmalıdır "), artırıcı (" ... içeren belgelerin puanını artırın ")

Desteklenen Belge Biçimleri
==========================
* Microsoft Office (doc, xls, ppt)
* Microsoft Office 2007 ve daha yenisi (docx, xlsx, pptx, docm, xlsm, pptm)
* Microsoft Outlook (pst)
* OpenOffice.org (odt, ods, odg, odp, ott, ots, otg, otp)
* Taşınabilir Belge Formatı (pdf)
* EPUB (epub)
* HTML (html, xhtml, ...)
* TXT ve diğer düz metin biçimleri (özelleştirilebilir)
* Zengin Metin Biçimi (rtf)
* AbiWord (abw, abw.gz, zabw)
* Microsoft Derlenmiş HTML Yardımı (chm)
* MP3 Meta Verileri (mp3)
* FLAC Meta Verileri (flac)
* JPEG Exif Meta Verileri (jpg, jpeg)
* Microsoft Visio (vsd)
* Ölçeklenebilir Vektör Grafikleri (svg)

Tasarım Felsefesi
=================
DocFetcher'ın tasarımı şu ilkeleri izler:

**Saçmalık içermez**: DocFetcher'ın kullanıcı arayüzü dağınıklıktan ve saçmalıklardan uzak olacak şekilde tasarlanmıştır. Sisteminize hiçbir işe yaramaz şey yüklenmez.

**Gizlilik**: DocFetcher, özel verilerinizi toplamaz, nokta. [Kaynak kodunu](https://sourceforge.net/p/docfetcher/wiki/Source%20code/) kontrol etmekte özgürsünüz.

**Yalnızca ihtiyacınız olanı indeksleme**: Diğer arama yazılımları varsayılan olarak tüm sabit diskinizi indeksler &mdash; belki "aptal" kullanıcılara uyum sağlamak için, onlardan kararları alarak veya daha fazla kullanıcı verisi toplamak için. Öte yandan DocFetcher varsayılan olarak *hiçbir şeyi* indekslemez ve indekslenecek veri seçimini kullanıcılara bırakır. Bu, tüm sabit disklerin indekslenmesinin genellikle arama sonuçlarının alakasız dosyalarla karışmasına da yol açan indeksleme zamanı ve disk alanının muazzam bir israfı olduğu gözlemine dayanır.

Dizin Oluşturma Nasıl Çalışır?
==================
Bu bölüm, indekslemenin ne olduğunu ve nasıl çalıştığını açıklar.

**Dosya aramaya saf yaklaşım**: Dosya aramaya yönelik en temel yaklaşım, bir arama yapıldığında belirli bir konumdaki her dosyayı tek tek ziyaret etmektir. Bu, *yalnızca dosya adı* araması için yeterince iyi çalışır, çünkü dosya adlarını analiz etmek çok hızlıdır. Bununla birlikte, dosyaların *içeriğini* aramak isteseydiniz o kadar iyi sonuç vermezdi, çünkü tam metin çıkarma, dosya adı analizinden çok daha pahalı bir işlemdir.

**Dizine dayalı arama**: Bu nedenle, bir içerik araştırmacısı olan DocFetcher, *dizinleme* olarak bilinen bir yaklaşımı benimsiyor: Temel fikir, insanların arama yapması gereken dosyaların çoğunun (% 95'ten fazlası gibi) çok seyrek olarak veya hiç değiştirilmemesidir. Bu nedenle, her aramada her dosyada tam metin çıkarma yapmak yerine, tüm dosyalarda yalnızca *bir kez* metin çıkarma gerçekleştirmek ve çıkarılan tüm metinden sözde *dizin* oluşturmak çok daha etkilidir. Bu dizin, dosyaların içerdikleri kelimelere göre hızlı bir şekilde aranmasını sağlayan bir sözlük gibidir.

**Telefon rehberi benzetimi**: Bir benzetme olarak, aradığınız kişinin diğer taraftaki kişi olup olmadığını anlamak için *her* olası telefon numarasını aramak yerine bir telefon rehberinde ("dizin") bir kişinin telefon numarasına bakmanın ne kadar verimli olduğunu düşünün. &mdash; Birini telefonla aramak ve bir dosyadan metin çıkarmak "pahalı işlemler" olarak kabul edilebilir. Ayrıca, insanların telefon numaralarını sık değiştirmemeleri, bilgisayardaki çoğu dosyanın nadiren değiştirildiği gerçeğine benzerdir.

**Dizin güncellemeleri**: Elbette bir dizin, dosyaların en son durumunu değil, yalnızca oluşturulduğu andaki dizine alınan dosyaların durumunu yansıtır. Dolayısıyla, dizin güncel tutulmazsa, tıpkı bir telefon rehberinin güncelliğini yitirmesi gibi, güncel olmayan arama sonuçları alabilirsiniz. Ancak, dosyaların çoğunun nadiren değiştirildiğini varsayabilirsek, bu çok da sorun olmamalıdır. Ek olarak, DocFetcher dizinlerini *otomatik olarak* güncelleyebilir: (1) Çalışırken, değişen dosyaları tespit eder ve buna göre dizinlerini günceller. (2) Çalış*madığında*, arka planda küçük bir arka plan programı değişiklikleri algılar ve güncellenecek dizinlerin bir listesini tutar; DocFetcher daha sonra bir sonraki başlatılışında bu dizinleri güncelleyecektir.
