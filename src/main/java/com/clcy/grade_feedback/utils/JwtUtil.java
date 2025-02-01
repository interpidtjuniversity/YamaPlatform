package com.clcy.grade_feedback.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.clcy.grade_feedback.model.UserLoginModel;

public class JwtUtil {

    // 使用固定的解密秘钥
    private static final String SECRET = "TOKEN_SECRET";

    public static String getToken(UserLoginModel userLogin) {
        try{
            //用秘钥生成签名
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            //默认头部+载荷（手机号/id）+签名=jwt
            String jwtToken= JWT.create()
                    .withClaim("studentId", userLogin.getStudentId())
                    .withClaim("password", userLogin.getPassword())
                    .sign(algorithm);
            return jwtToken;
        }catch (Exception e){
            return null;
        }
    }

    public static UserLoginModel verify(String token) {
        try {
            // 根据用户信息userInfo生成JWT效验器
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            JWTVerifier verifier = JWT.require(algorithm)
                    .build();
            // 效验TOKEN
            verifier.verify(token);
            //返回token内容
            return getTokenInfo(token);
        } catch (Exception exception) {
            return null;
        }
    }

    public static UserLoginModel getTokenInfo(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            UserLoginModel userLogin= UserLoginModel.builder()
                    .studentId(jwt.getClaim("studentId").asString())
                    .password(jwt.getClaim("password").asString())
                    .build();
            return userLogin;
        } catch (JWTDecodeException e) {
            return null;
        }
    }
}

