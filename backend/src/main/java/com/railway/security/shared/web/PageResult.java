package com.railway.security.shared.web;

import java.util.List;

public record PageResult<T>(long total, List<T> records) {}
