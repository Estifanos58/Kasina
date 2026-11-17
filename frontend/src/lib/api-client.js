import axios from "axios"
import { HOST } from "../utils/constants.js"

export const apiClient = axios.create({
    withCredentials: true,
    baseURL: HOST,
})