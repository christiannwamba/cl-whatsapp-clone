package i.am.eipeks.cloudinary;


import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface Upload {

    @FormUrlEncoded
    @POST("messages")
    Call<Void> message(@Field("message") String message, @Field("user") String user);

    @FormUrlEncoded
    @POST("messages")
    Call<Void> picture(@Field("message") String message, @Field("user") String user,
                       @Field("image") String imageLink);

}
