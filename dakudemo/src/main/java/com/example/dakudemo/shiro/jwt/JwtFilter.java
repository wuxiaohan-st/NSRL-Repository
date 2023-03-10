package com.example.dakudemo.shiro.jwt;

import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.example.dakudemo.shiro.jwt1.Constant;
import com.example.dakudemo.util.JsonCovertUtils;
import com.example.dakudemo.util.JwtTokenUtils;
import com.example.dakudemo.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.ObjectUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class JwtFilter extends AuthenticatingFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    private String redirectUrl;

    private String authcScheme = HttpServletRequest.BASIC_AUTH;

    protected static final String AUTHENTICATE_HEADER = "WWW-Authenticate";

    protected static final String AUTHORIZATION_HEADER = "Authorization";

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    private JwtTokenUtils jwtTokenUtil;

    public JwtTokenUtils getJwtTokenUtil() {
        return jwtTokenUtil;
    }

    public void setJwtTokenUtil(JwtTokenUtils jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public String getAuthcScheme() {
        return authcScheme;
    }

    public void setAuthcScheme(String authcScheme) {
        this.authcScheme = authcScheme;
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        String token = getRequestToken((HttpServletRequest)request);
        if (!ObjectUtils.isEmpty(token)) {
            try {
                // ??????Shiro?????????UserRealm
                this.executeLogin(request, response);
            } catch (Exception e) {
                // ???????????????????????????????????????msg
                String msg = e.getMessage();
                // ??????????????????(???Cause??????????????????throwable(??????)???throwable(??????))
                Throwable throwable = e.getCause();
                if (throwable instanceof SignatureVerificationException) {
                    // ????????????JWT???AccessToken????????????(Token?????????????????????)
                    msg = "Token?????????????????????(" + throwable.getMessage() + ")";
                } else if (throwable instanceof TokenExpiredException) {
                    // ????????????JWT???AccessToken??????????????????RefreshToken??????????????????AccessToken??????
//                    if (this.refreshToken(request, response)) {
//                        return true;
//                    } else {
//                        msg = "Token?????????(" + throwable.getMessage() + ")";
//                    }
                    return false;
                } else {
                    // ?????????????????????
                    if (throwable != null) {
                        // ??????????????????msg
                        msg = throwable.getMessage();
                    }
                }
                // Token????????????????????????Response??????
                try {
                    ((HttpServletResponse) response).sendRedirect(redirectUrl);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                //this.response401(response, msg);
                return false;
            }
        } else {
            // ????????????Token
            HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
            // ????????????????????????
            String httpMethod = httpServletRequest.getMethod();
            // ??????????????????URI
            String requestURI = httpServletRequest.getRequestURI();
            logger.info("???????????? {} Authorization??????(Token)?????? ???????????? {}", requestURI, httpMethod);
            // mustLoginFlag = true ??????????????????????????????????????????
            final Boolean mustLoginFlag = true;
            if (mustLoginFlag) {
                //this.response401(response, "????????????");
                try {
                    ((HttpServletResponse) response).sendRedirect(redirectUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        this.sendChallenge(request, response);
        return false;
    }



    protected boolean isLoginAttempt(ServletRequest request, ServletResponse response) {
        // ????????????Header???Authorization???AccessToken(Shiro???getAuthzHeader??????????????????)
        String token = this.getAuthzHeader(request);
        return token != null;
    }

    @Override
    protected boolean executeLogin(ServletRequest request, ServletResponse response) throws Exception {
        // ????????????Header???Authorization???AccessToken(Shiro???getAuthzHeader??????????????????)
        JwtToken token = new JwtToken(this.getAuthzHeader(request));
        // ?????????UserRealm?????????????????????????????????????????????????????????
        this.getSubject(request, response).login(token);
        // ??????????????????????????????????????????????????????true
        return true;
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        String token = getRequestToken(httpRequest);
        JwtToken jwtToken = new JwtToken();
        jwtToken.setToken(token);
        return jwtToken;
    }

    protected boolean sendChallenge(ServletRequest request, ServletResponse response) {
        log.debug("Authentication required: sending 401 Authentication challenge response.");

        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String authcHeader = getAuthcScheme() + " realm=\"" + "application" + "\"";
        httpResponse.setHeader(AUTHENTICATE_HEADER, authcHeader);
        return false;
    }

    protected String getAuthzHeader(ServletRequest request) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        return httpRequest.getHeader(AUTHORIZATION_HEADER);
    }
















    //??????????????????header,params,cookie?????????token
    protected String getRequestToken(HttpServletRequest request){
        String token = request.getHeader(Constant.TOKEN);
        if(StringUtils.isBlank(token)){
            token = request.getParameter(Constant.TOKEN);
        }
        if (StringUtils.isBlank(token)) {
            token = request.getParameter("sx_sso_sessionid");
        }
        if(StringUtils.isBlank(token)){
            Cookie[] cookies = request.getCookies();
            if(cookies != null && cookies.length > 0){
                for(Cookie cookie : cookies){
                    if(Constant.TOKEN.equals(cookie.getName())){
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }
        return token;
    }

    //???????????????ip??????
    public static String getIPAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("x-forwarded-for");
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
            String localIp = "127.0.0.1";
            String localIpv6 = "0:0:0:0:0:0:0:1";
            if (ipAddress.equals(localIp) || ipAddress.equals(localIpv6)) {
                // ??????????????????????????????IP
                InetAddress inet = null;
                try {
                    inet = InetAddress.getLocalHost();
                    ipAddress = inet.getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
        // ?????????????????????????????????????????????IP??????????????????IP,??????IP??????','??????
        String ipSeparate = ",";
        int ipLength = 15;
        if (ipAddress != null && ipAddress.length() > ipLength) {
            if (ipAddress.indexOf(ipSeparate) > 0) {
                ipAddress = ipAddress.substring(0, ipAddress.indexOf(ipSeparate));
            }
        }
        return ipAddress;
    }


    private void ResponseMsg(String msg, ServletResponse response){
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        httpServletResponse.setStatus(HttpStatus.OK.value());
        httpServletResponse.setCharacterEncoding("utf-8");
        httpServletResponse.setContentType("text/html");
        try {
            PrintWriter out = httpServletResponse.getWriter();
            out.append(msg);
        }catch (Exception e){
            log.error("????????????Response????????????"+ e.getMessage());
            e.printStackTrace();
        }
    }

    private void response401(ServletResponse response, String msg) {
        HttpServletResponse httpServletResponse = WebUtils.toHttp(response);
        httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        httpServletResponse.setCharacterEncoding("UTF-8");
        httpServletResponse.setContentType("application/json; charset=utf-8");
        try (PrintWriter out = httpServletResponse.getWriter()) {
            String data = JsonCovertUtils.objToJson( "????????????(Unauthorized):" + msg);
            out.append(data);
        } catch (IOException e) {
            logger.error("????????????Response????????????IOException??????:{}", e.getMessage());
//            throw new CustomException("????????????Response????????????IOException??????:" + e.getMessage());
        }
    }
}
