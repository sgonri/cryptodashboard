package com.mycompany.app.service;

import com.mycompany.app.model.Crypto;
import java.util.List;

public interface CryptoService {
    List<Crypto> getTopCryptos();
}
