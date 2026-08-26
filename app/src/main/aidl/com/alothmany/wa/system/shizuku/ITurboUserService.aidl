package com.alothmany.wa.system.shizuku;

interface ITurboUserService {
    void destroy() = 16777114;
    String exec(String command) = 1;
    int uid() = 2;
    int pid() = 3;
}
