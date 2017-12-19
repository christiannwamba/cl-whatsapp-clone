package i.am.eipeks.cloudinary;


import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.gson.Gson;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Chat extends AppCompatActivity implements View.OnClickListener {

    private EditText typedMessage;
    private ImageButton sendMessage;
    private ImageView localImage;
    private ListView messagesList;
    private Upload upload;
    private Uri uri;
    private boolean hasUploadedPicture = false;
    private String imagePath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        MediaManager.init(this);
        upload = RetrofitUtils.getRetrofit().create(Upload.class);

        typedMessage = findViewById(R.id.typed_message);
        sendMessage = findViewById(R.id.send);
        ImageButton loadImage = findViewById(R.id.load_image);
        localImage = findViewById(R.id.local_image);
        messagesList = findViewById(R.id.messages);

        if (TextUtils.isEmpty(typedMessage.getText())){
            sendMessage.setEnabled(false);
        } else {
            sendMessage.setEnabled(true);
        }

        final List<Message> messages = new ArrayList<>();
        messagesList.setAdapter(new MessageAdapter(this, messages));

        loadImage.setOnClickListener(this);
        sendMessage.setOnClickListener(this);
        typedMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)){
                    sendMessage.setEnabled(false);
                } else {
                    sendMessage.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        PusherOptions options = new PusherOptions();
        options.setCluster(Constants.PUSHER_CLUSTER_TYPE);
        Pusher pusher = new Pusher(Constants.PUSHER_KEY, options);
        Channel channel = pusher.subscribe("messages");
        channel.bind("new-message", new SubscriptionEventListener() {
            @Override
            public void onEvent(String channelName, String eventName, final String data) {
                Gson gson = new Gson();
                final Message message = gson.fromJson(data, Message.class);
                if (hasUploadedPicture){
                    message.messageType = Constants.IMAGE;
                } else {
                    message.messageType = Constants.TEXT;
                }
                hasUploadedPicture = false;
                messages.add(message);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messagesList.setSelection(messagesList.getAdapter().getCount() - 1);
                    }
                });
            }
        });
        pusher.connect();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.send:
//                makeToast("Send clicked");
                if (hasUploadedPicture){
                    //unsigned upload
                    String requestId = MediaManager.get()
                            .upload(uri)
                            .unsigned("myPreset")
                            .option("resource_type", "image")
                            .callback(new UploadCallback() {
                                @Override
                                public void onStart(String requestId) {
                                    makeToast("Uploading...");
                                }

                                @Override
                                public void onProgress(String requestId, long bytes, long totalBytes) {

                                }

                                @Override
                                public void onSuccess(String requestId, Map resultData) {
                                    makeToast("Upload finished");
                                    imagePath = MediaManager.get().url().generate(resultData.get("public_id").toString().concat(".jpg"));
                                    uploadToPusher();
                                }

                                @Override
                                public void onError(String requestId, ErrorInfo error) {
                                    makeToast("An error occurred.\n" + error.getDescription());
                                }

                                @Override
                                public void onReschedule(String requestId, ErrorInfo error) {
                                    makeToast("Upload rescheduled\n" + error.getDescription());
                                }
                            }).dispatch();
                } else {
                    upload.message(typedMessage.getText().toString(), "Eipeks").enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                            switch (response.code()){
                                case 200:
                                    typedMessage.setText("");
                                    break;
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            Toast.makeText(Chat.this, "Error uploading message", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                break;
            case R.id.load_image:
                Intent  chooseImage = new Intent();
                chooseImage.setType("image/*");
                chooseImage.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(chooseImage, "Select Picture"), Constants.IMAGE_CHOOSER_INTENT);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.IMAGE_CHOOSER_INTENT && resultCode == RESULT_OK){
            if (data != null && data.getData() != null){
                uri = data.getData();
                hasUploadedPicture = true;
                String localImagePath = getRealPathFromURI(uri);
                Bitmap bitmap;
                try {
                    InputStream stream = getContentResolver().openInputStream(uri);
                    bitmap = BitmapFactory.decodeStream(stream);
                    localImage.setVisibility(View.VISIBLE);
                    localImage.setImageBitmap(bitmap);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                imagePath = MediaManager.get().url().generate(getFileName(uri));
                typedMessage.setText(localImagePath);
            }
        }
    }

    private void uploadToPusher(){
        upload.picture(typedMessage.getText().toString(), "Eipeks", imagePath)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        switch (response.code()){
                            case 200:
                                localImage.setVisibility(View.GONE);
                                typedMessage.setText("");
                                break;
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        Toast.makeText(Chat.this, "Failed to upload picture\n" + t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] projection = { MediaStore.Images.Media.DATA };
            cursor = getContentResolver().query(contentUri,  projection, null, null, null);
            @SuppressWarnings("ConstantConditions")
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                //noinspection ConstantConditions
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void makeToast(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
