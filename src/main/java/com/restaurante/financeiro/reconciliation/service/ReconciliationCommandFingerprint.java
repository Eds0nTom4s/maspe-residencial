package com.restaurante.financeiro.reconciliation.service;
import com.restaurante.financeiro.reconciliation.model.ReconciliationCaseAction;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Component
public class ReconciliationCommandFingerprint {
    public static final String CONTRACT_VERSION="1";
    public String calculate(Long caseId, ReconciliationCaseAction action, Map<String,?> payload){
        StringBuilder s=new StringBuilder("caseId=").append(caseId).append("|action=").append(action).append("|contract=").append(CONTRACT_VERSION);
        new TreeMap<>(payload).forEach((k,v)->s.append('|').append(k).append('=').append(normalize(v)));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
    }
    private String normalize(Object v){return v==null?"":v.toString().trim().replaceAll("\\s+"," ");}
}
