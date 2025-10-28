package com.mycompany.app.service;

import com.mycompany.app.model.News;
import java.util.List;

public interface NewsService {
    List<News> getNewsForCrypto(String cryptoId);
}
