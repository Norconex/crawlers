/* Copyright 2015-2020 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.crawler;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.xml.XML;

/**
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfigTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawlerConfigTest.class);

    public class TESTRedirectStrategy extends DefaultRedirectStrategy {

        public TESTRedirectStrategy() {
            super(new String[] {});
        }

    }
    @Test
    public void givenRedirectsAreDisabled_whenConsumingUrlWhichRedirects_thenNotRedirected()
      throws ClientProtocolException, IOException {
        HttpClient instance = HttpClientBuilder.create()
                //.setRedirectStrategy(new TESTRedirectStrategy())
                .disableRedirectHandling()
                .build();


        HttpGet get = new HttpGet("https://www.toyota.com/priusc/features/safety");
//        get.addHeader("Host",  "www.toyota.com");
//        get.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:76.0) Gecko/20100101 Firefox/76.0");
//        get.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
//        get.addHeader("Accept-Language", "en-US,en;q=0.5");
//        get.addHeader("Accept-Encoding", "gzip, deflate, br");
//        get.addHeader("DNT", "1");
//        get.addHeader("Connection", "keep-alive");

//        get.addHeader(Cookie: AMCV_8F8B67C25245B30D0A490D4C%40AdobeOrg=-1176276602%7CMCMID%7C63268834161488855093733759281059572042%7CMCAID%7CNONE%7CMCOPTOUT-1589947434s%7CNONE%7CMCAAMLH-1590032581%7C7%7CMCAAMB-1590545034%7Cj8Odv6LonN4r3an7LhD3WZrU1bUpAkFkkiY1ncBR96t2PTI%7CMCCIDH%7C-2082197881; TOYOTANATIONAL_ENSIGHTEN_PRIVACY_MODAL_VIEWED=1; _gcl_au=1.1.2113869178.1588024614; s_ecid=MCMID%7C63268834161488855093733759281059572042; _ga=GA1.2.3861923251739502_7847459108837770; mbox=PC#71edd7313e9346958f4da43ab3707f74.17_0#1651269417|session#9df9ce8ed3b34c148284f328704b825e#1589942095; _sd:user=763cd528-7997-46eb-849e-63f7cecc7c10%3A2.43%3A1588024615833%3A3ki4ohju7b14m!ec4c6c178773985831316ad797d053b1!1lp053jup9801!%3A29524!29524!29524!; user_email_id=; tms_firstVisitEver=1588024617240; tms_visitList={"visits":["1589940234876","1589865498875","1589574918586","1589517898135","1589427782911","1589390131031","1589339792098","1588787403868","1588187919529","1588183227626","1588126357778","1588060742069","1588040910409","1588024617242"],"v90":0,"lastVisit":"1589865498875"}; tms_firstReferrer=; uuid=884b7474-35e3-404d-a885-53bbcf51b17d; tmsvisitor=3861923251739502_7847459108837770; s_vi=3861923251739502_7847459108837770; s_lv=1589940234923; s_pers=%20s_vnum%3D1590984000508%2526vn%253D6%7C1590984000508%3B%20s_nr%3D1589508508648-Repeat%7C1592100508648%3B%20s_fid%3D71C8F202D41ACF7D-21C79E71902A627D%7C1747706634944%3B; tms_vi=3861923251739502_7847459108837770; _fbp=fb.1.1588024624273.2094427195; visid_incap_1073739=8+NUfGosSTi5o7VBxJV0pQvTqV4AAAAAQUIPAAAAAACz8KK75JYx2pveBPe+Jm3Z; TOYOTANATIONAL_ENSIGHTEN_PRIVACY_MODAL_VIEWED=1; _tmna_ga=GA1.2.3861923251739502_7847459108837770; AMCV_8F8B67C25245B30D0A490D4C%40AdobeOrg=-1330315163%7CMCMID%7C63268834161488855093733759281059572042%7CMCAID%7CNONE%7CMCOPTOUT-1588195124s%7CNONE%7CMCAAMLH-1588792724%7C7%7CMCAAMB-1588792724%7Cj8Odv6LonN4r3an7LhD3WZrU1bUpAkFkkiY1ncBR96t2PTI%7CMCIDTS%7C18382; visid_incap_1250125=mbhH/JKMQDKoIcKNtpKv56DWvF4AAAAAQUIPAAAAAADA7f4oD7rHYO6hyrnaOP5b; s_vnum=1590984000207%26vn%3D4; s_nr=1589940234923-Repeat; ugzipcode=%7B%22zipcode%22%3A%2290210%22%2C%22type%22%3A%22user%22%7D; zipcode=90210; state=CA; tda=SOC10; pxw=1280; pxrw=3840; check=true; lastSeriesVisited={}; tcom-dealer-code=Toyota; xact=18235893ffb9cbfd_f35aae05854fe7b7; AMCVS_8F8B67C25245B30D0A490D4C%40AdobeOrg=1; s_sess=%20s_sq%3D%3B%20s_cc%3Dtrue%3B; ipe_s=22f33bbf-41d4-aad7-a350-1f0a7714d770; ipe.30434.pageViewedCount=7; ipe.30434.pageViewedDay=72; ADRUM=s=1589942013211&r=https%3A%2F%2Fwww.toyota.com%2Fsocal%2Fdeals-incentives%2F%3F0; s_sq=%5B%5BB%5D%5D; c_m=Other%20Natural%20Referrersundefinedwww.toyota.com; nlbi_1073739=jMz8DcoDB0ZY3+HRDdJh9AAAAACgq1Z+IZmmwqHslIaHC6Eq; AWSELB=2F15E1FF1440769F00C823B7DA1A2201321387993A751C25F958C9E06493FEC0FF9636D396CE0BCE98517C319796BD9D7A3662D87C5AC740D58E00A09D0EFDE1CF334E113A; AWSELBCORS=2F15E1FF1440769F00C823B7DA1A2201321387993A751C25F958C9E06493FEC0FF9636D396CE0BCE98517C319796BD9D7A3662D87C5AC740D58E00A09D0EFDE1CF334E113A; nlbi_1073739_1308816=5Qg/bGLGmHoxTdokDdJh9AAAAACqAcg5q+k8q84yiWrMn5Cg; flyout-lohp=1; nlbi_1250125=CcTYEJANdUsQDO0h7gNUAgAAAABm4JEhfQObM0bklVaY0QJW; incap_ses_1229_1250125=47p2JmKahXp/cQXFgUkOEaDWvF4AAAAAVS3codSRMa3bsnbyrzJ45w==; _sd:session=75fa2cab-de8b-49bb-8357-a69caced49df%3AN%3A1589942116822%3A%3A3ki4ohju7b14m!ec4c6c178773985831316ad797d053b1!1lp053jup9801!%3A1589517899038%3AN%3Aproduction; entune_user_selection=%7B%7D; tda-multiple=04154; OAMAuthnHintCookie=0@1589570234; da_percent=53; _tmna_ga_kpi=%7B%22expiry%22%3A1589951896687%2C%22traffic%22%3Atrue%2C%22sem%22%3Atrue%7D; _tmna_ga_gid=GA1.2.548746166.1589865498; nsr_qual=1; _uetsid=852061e0-9622-71d8-94ac-02af1903c5d1

//        get.addHeader("Upgrade-Insecure-Requests", "1");


        HttpResponse response = instance.execute(get);




System.out.println("STATUS: " + response.getStatusLine());
for (Header h : response.getAllHeaders()) {
    System.err.println("  " + h.getName() + " : " + h.getValue());
}
//        assertEquals(302,  response.getStatusLine().getStatusCode());
    }

    @Test
    public void testWriteRead() {
//        File configFile = new File(
////                "src/site/resources/examples/minimum/minimum-config.xml");
//                "src/site/resources/examples/complex/complex-config.xml");
//        HttpCollectorConfig config = (HttpCollectorConfig)
//                new CollectorConfigLoader(HttpCollectorConfig.class)
//                        .loadCollectorConfig(configFile);

        HttpCollectorConfig config = new HttpCollectorConfig();

        XML xml = new ConfigurationLoader().loadXML(Paths.get(
                "src/site/resources/examples/complex/complex-config.xml"));
        xml.populate(config);
//        new XML(new ConfigurationLoader().loadXML(
//                ).configure(config);

        HttpCrawlerConfig crawlerConfig =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(0);
        GenericHttpFetcher fetcher =
                (GenericHttpFetcher) crawlerConfig.getHttpFetchers().get(0);
        fetcher.getConfig().setRequestHeader("header1", "value1");
        fetcher.getConfig().setRequestHeader("header2", "value2");
        fetcher.getConfig().getProxySettings().getCredentials().setPasswordKey(
                new EncryptionKey("C:\\keys\\myEncryptionKey.txt",
                        EncryptionKey.Source.FILE));
        fetcher.getConfig().getAuthCredentials().setPasswordKey(
                new EncryptionKey("my key"));

        crawlerConfig.setStartURLsProviders(new MockStartURLsProvider());


        LOG.debug("Writing/Reading this: {}", config);
        XML.assertWriteRead(config, "httpcollector");
//        assertWriteRead(config);
    }


//    public static void assertWriteRead(IXMLConfigurable xmlConfiurable)
//            throws IOException {
//
//        // Write
//        Writer out = new OutputStreamWriter(System.out);
//        try {
//            xmlConfiurable.saveToXML(out);
//        } finally {
//            out.close();
//        }
//    }

}
