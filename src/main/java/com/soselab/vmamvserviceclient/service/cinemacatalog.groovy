package com.soselab.vmamvserviceclient.service

import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            description ("ordering contract")
            name ("cinemacatalog_validate_prime-number")
            request {
                method ("GET")
                url("/validate/prime-number") {
                    queryParameters {
                        parameter("number", "2")
                    }
                }
            }
            response {
                body("Even")
                status (200)
            }
        },
        Contract.make {
            description ("ordering contract")
            name ("cinemacatalog_newMovieOrdering")
            request {
                method ("GET")
                url("/newMovieOrdering") {
                    queryParameters {
                        parameter("moviesID", "5e0b10fa974ef74883b43403")
                    }
                }
            }
            response {
                body("success")
                status (200)
            }
        },
        Contract.make {
            description ("ordering contract")
            name ("cinemacatalog_getMovieFromOrderList")
            request {
                method ("GET")
                url("/getMovieFromOrderList") {
                    queryParameters {
                        parameter("userID", "1")
                    }
                }
            }
            response {
                status (200)
            }
        }
]