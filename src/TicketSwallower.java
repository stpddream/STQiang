
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

/**
 * Class that connects to Haverticket and sends out ticket reservation.
 * Process:
 *
 * 1. getAuthURl():
 *    Sends out a GET request to Haverticket site and will be redirected to Haverford Authentication site. Returns the authentication site url.
 *
 * 2. authenticate():
 *    Send a POST request to the authentication site with user's username and password. If authenication succeeded, will be redirected to Haverticket site.
 *
 * 3. getCookieAuth():
 *    The first time log on to Haverticket site, agent will be given a PHP session ID and authentication token. This stores the cookie in cookiestore.
 *
 * 4. submitRes():
 *    Get to the page that user fills out their name and friends name. Submit the information.
 *
 * 5. swallowTicket():
 *    Go to the confirmation page and submit the reservation!
 *
 */
public class TicketSwallower {

    private static final String SITE_URL = "http://go.haverford.edu/tickets";
    private static final String BASE_RESERVE_URL = "https://go.haverford.edu/tickets/?mode=reserve&";

    private String username;
    private String password;
    private String eventId;
    private String name;
    private final String USER_AGENT = "Mozilla/5.0";

    private int retry = 3;
    private String reserveUrl;

    private RequestConfig globalRequestConf;
    private CookieStore cookieStore;
    private STQiang.StatusUpdater updater;
    private FileWriter fw;

    public TicketSwallower(String username, String password, String name, String eventId, STQiang.StatusUpdater updater) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.eventId = eventId;
        this.reserveUrl = BASE_RESERVE_URL + "event_id=" + this.eventId;

        try {
            fw = new FileWriter("cornie.log");
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        this.updater = updater;
        cookieStore = new BasicCookieStore();
        globalRequestConf = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BEST_MATCH)
                .build();

    }


    /**
     * Start reserving tickets!
     * @return reservation status.
     *          0 for success.
     *          -1 for authentication failure.
     *          -2 for resubmitting reservation (user already has a ticket).
     */
    public int startEngine() throws IOException {

            try {
            updater.updateStatus("Starting the Qiang! engine...");
            log("Starting Qiang! engine...");
            String realAdd = this.authenticate(this.getAuthUrl());
            if(realAdd == null) return -1; //Authentication Failure
            getCookieAuth(realAdd);

            /**
             * Status code for submitting reservation:
             * STATUS 0 Success. Proceed to confirmation page.
             * STATUS 1 Tickets are not posted yet.
             * STATUS -2 User already has a ticket.
             */
            while(true) {

                int status = submitRes();
                if(status == 0) break;
                else if(status == -2) return -2;
                try {
                    Thread.sleep(10000); //If submit res returns true; It may be that tickets are not on sale yet, try again in 10s.
                } catch (InterruptedException e) {}

            }
            swallowTicket();
            } catch(IOException e) {
                updater.updateStatus("Connection Error.");
                log("!!!!!!!!! Connection Error. !!!!!!!!!");
                throw new IOException();
            }


        return 0;

    }

    public boolean testAuth() throws IOException {

        updater.updateStatus("Validating Password...");
        log("Validating Password...");

            String realAdd = this.authenticate(this.getAuthUrl());
            if(realAdd == null) return false; //Authentication Failure

        updater.updateStatus("Validated. Waiting for the ticket....");
        log("Validated. Waiting for th ticket");
        return true;

    }

    private String getAuthUrl() throws IOException {

        HttpClient httpClient = HttpClientBuilder.create()
                .disableRedirectHandling()
                .setDefaultCookieStore(new BasicCookieStore())
                .build();

        String redirectURL = SITE_URL;
        String oldDirectURL = null;


        //Loop until authentication url is retrieved
        System.out.print("Connecting...");
        updater.updateStatus("Connecting to original url...");
        log("Connecting to original url");

        do {

            HttpGet request = new HttpGet(redirectURL);
            request.addHeader("User-Agent", USER_AGENT);
            HttpResponse response = null;

            response = httpClient.execute(request);
            oldDirectURL = redirectURL;

            if(response.getFirstHeader("Location") == null) break;        //Until response status code 302 - redirect
            redirectURL = response.getFirstHeader("Location").getValue();


        } while(true);

        updater.updateStatus("Login site connected");
        System.out.println("Login site connected.");
        log("Login site connected");
        return oldDirectURL;
    }

    private String authenticate(String url) throws IOException{


        int attempt = 0;
        HttpResponse response = null;

        while(attempt < retry) {

            attempt++;

            HttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultCookieStore(new BasicCookieStore())  //Cookies are ignored
                    .build();

            System.out.print("Authorizing username and password...");
            updater.updateStatus("Authorizing username and password...");
            log("Authorizing...");

            HttpPost post = new HttpPost(url);
            post.addHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setConfig(globalRequestConf);

            //TODO: Response Token Encoding Problem: "%20" or "+"
            /* Update: No Problem???? */

            //rt and st are already in encoded form (retrived from url)

            String rt = URLDecoder.decode(this.getParam(url, "RT"), "utf-8");
            String st = URLDecoder.decode(this.getParam(url, "ST"), "utf-8");

            /* Request Contents */
            List<BasicNameValuePair> urlParams = new ArrayList<BasicNameValuePair>();
            urlParams.add(new BasicNameValuePair("RT", rt));
            urlParams.add(new BasicNameValuePair("ST", st));
            urlParams.add(new BasicNameValuePair("LC", ""));
            urlParams.add(new BasicNameValuePair("login", "yes"));
            urlParams.add(new BasicNameValuePair("username", username));
            urlParams.add(new BasicNameValuePair("initial_username", username));
            urlParams.add(new BasicNameValuePair("password", password));
            urlParams.add(new BasicNameValuePair("institution", "haverford"));

            UrlEncodedFormEntity encode = new UrlEncodedFormEntity(urlParams);
            encode.setContentType("application/x-www-form-urlencoded");
            encode.setContentEncoding("UTF-8");
            post.setEntity(encode);


            /* Retrieve True URL */
            //TODO: more elegant way to retrieve redirecting URL
            response = httpClient.execute(post);
            String responseStr = parseResponse(response);

            //Stupid way of judging error
            if(!responseStr.contains("<!-- Error: login failed. -->")) {
                System.out.println(response.getStatusLine().getStatusCode());
                System.out.println("Authorized. Redirecting to HaverTicket...");
                updater.updateStatus("Authorized. Redirecting to HaverTicket...");
                log("Authorized. Redirecting to HaverTicket");

                return this.getRedirect(responseStr);
            }

        }

        return null;
    }



    private void getCookieAuth(String url) throws IOException {

        HttpClient client = HttpClientBuilder.create()
                .setDefaultCookieStore(cookieStore)
                .build();

        System.out.println("Connecting to Haverticket...");
        updater.updateStatus("Connecting to Haverticket...");
        log("Connecting to Haverticket");

        HttpGet request = new HttpGet(url);
        HttpResponse response = null;

        response = client.execute(request);

        BufferedReader br = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        System.out.println(response.getStatusLine().getStatusCode());
        System.out.println(response);

        String line;
        StringBuilder strBody = new StringBuilder();
        while((line = br.readLine()) != null) {
            strBody.append(line);
        }

        System.out.println(strBody.toString());

    }

    private int submitRes() throws IOException {

        HttpClient client = HttpClientBuilder.create()
                .setDefaultCookieStore(cookieStore)
                .build();

        System.out.print("Start reserving ticket...");
        updater.updateStatus("Start reserving ticket...");
        log("Start reserving ticket");

        HttpPost post = new HttpPost(reserveUrl);
        post.setHeader("Content-Type", "multipart/form-data; boundary=---------------------------7dd4f26160556");

        Iterator<Cookie> cookieIterator = cookieStore.getCookies().iterator();

        while(cookieIterator.hasNext()) {
            Cookie thisCookie = cookieIterator.next();
            System.out.println(thisCookie.getName() + ": " + thisCookie.getValue());

        }

        MultipartEntityBuilder builder = MultipartEntityBuilder.create().
                setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setBoundary("---------------------------7dd4f26160556");


        builder.addPart("username", new StringBody(username, ContentType.MULTIPART_FORM_DATA));
        builder.addPart("event_id", new StringBody(eventId, ContentType.MULTIPART_FORM_DATA));
        builder.addPart("mode", new StringBody("reserve", ContentType.MULTIPART_FORM_DATA));
        builder.addPart("reserve_a_ticket", new StringBody("Reserve A Ticket", ContentType.MULTIPART_FORM_DATA));

        HttpEntity formEntity = builder.build();

        post.setEntity(formEntity);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        formEntity.writeTo(bytes);
        String content = bytes.toString();

        System.out.println(content);
        log(content);

        HttpResponse response = client.execute(post);
        System.out.println(response.getStatusLine().getStatusCode());
        String res = parseResponse(response);
        if(res.contains("already have a ticket")) return -2;
        if(res.contains("Tickets Go On Sale")) return 1;


        /** TODO: Simplify log information **/
        System.out.println("Redirected to forms.");
        updater.updateStatus("Redirecting to confirmation page....");
        log("Redirecting to confirmation page...");
        return 0;
    }

    private boolean swallowTicket() throws IOException {

        HttpClient client = HttpClientBuilder.create()
                .setDefaultCookieStore(cookieStore)
                .build();

        System.out.println(cookieStore.getCookies().size());
        System.out.print("Submitting reservation...");
        updater.updateStatus("Submitting reservation...");
        log("Submitting reservation");

        HttpPost post = new HttpPost(reserveUrl);

        System.out.println("Connecting: " + reserveUrl);
        post.setHeader("Content-Type", "multipart/form-data; boundary=---------------------------7dd214302059c");
        post.setConfig(globalRequestConf);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create().
                setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setBoundary("---------------------------7dd214302059c");

        builder.addPart("confirm_ticket", new StringBody("Yes, Reserve My Ticket(s)!", ContentType.MULTIPART_FORM_DATA));
        builder.addPart("name", new StringBody(name, ContentType.MULTIPART_FORM_DATA));
        builder.addPart("cell", new StringBody("", ContentType.MULTIPART_FORM_DATA));

        HttpEntity formEntity = builder.build();

        post.setEntity(formEntity);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();


        formEntity.writeTo(bytes);
        String content = bytes.toString();

        System.out.println(content);
        log(content);

        HttpResponse response = client.execute(post);
        System.out.println(response.getStatusLine().getStatusCode());
        parseResponse(response);

        System.out.println("Complete.");
        updater.updateStatus("Tickets SWALLOWED. Enjoy!");
        log("Complete.");
        return true;
    }




    private String parseResponse(HttpResponse response) throws IOException {

        BufferedReader br = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        String line;
        StringBuilder strBody = new StringBuilder();
        while((line = br.readLine()) != null) {
            strBody.append(line);
        }

        System.out.println(strBody.toString());
        log("**************************************************************");
        log(strBody.toString());
        log("**************************************************************");


        return strBody.toString();

    }
    private String getParam(String url, String pattern) {
        String sub = url.substring(url.indexOf(pattern) + pattern.length() + 1);
        return sub.substring(0, sub.indexOf(';'));
    }

    private String getRedirect(String page) {
        System.out.println("Page" + page);
        String keyword = "top.location";
        String sub = page.substring(page.indexOf(keyword) + keyword.length() + 2);
        return sub.substring(0, sub.indexOf("\";"));
    }


    public void log(String content) throws IOException {

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Calendar cal = Calendar.getInstance();

        fw.write("[" + dateFormat.format(cal.getTime()) + "]  " );
        fw.write(content + "\n");
        fw.flush();


    }

}
